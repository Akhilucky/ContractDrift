package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/IBM/sarama"
)

type TrafficSample struct {
	Timestamp   string            `json:"timestamp"`
	Source      string            `json:"source"`
	Destination string            `json:"destination"`
	Method      string            `json:"method"`
	Path        string            `json:"path"`
	StatusCode  int               `json:"status_code"`
	ReqHeaders  map[string]string `json:"request_headers,omitempty"`
	RespHeaders map[string]string `json:"response_headers,omitempty"`
	ReqBody     string            `json:"request_body,omitempty"`
	RespBody    string            `json:"response_body,omitempty"`
	DurationMs  int64             `json:"duration_ms"`
	Environment string            `json:"environment"`
}

type config struct {
	KafkaBrokers  []string
	KafkaTopic    string
	FilePath      string
	PollInterval  time.Duration
}

func loadConfig() config {
	brokers := os.Getenv("KAFKA_BROKERS")
	if brokers == "" {
		brokers = "kafka:9092"
	}
	topic := os.Getenv("TRAFFIC_TOPIC")
	if topic == "" {
		topic = "raw-traffic"
	}
	filePath := os.Getenv("TRAFFIC_FILE_PATH")
	if filePath == "" {
		filePath = "/tmp/traffic_samples.ndjson"
	}

	return config{
		KafkaBrokers: strings.Split(brokers, ","),
		KafkaTopic:   topic,
		FilePath:     filePath,
		PollInterval: 500 * time.Millisecond,
	}
}

func main() {
	cfg := loadConfig()

	log.Printf("tailer starting: file=%s topic=%s brokers=%v", cfg.FilePath, cfg.KafkaTopic, cfg.KafkaBrokers)

	saramaCfg := sarama.NewConfig()
	saramaCfg.Producer.RequiredAcks = sarama.WaitForLocal
	saramaCfg.Producer.Compression = sarama.CompressionSnappy
	saramaCfg.Producer.Flush.Frequency = 500 * time.Millisecond
	saramaCfg.Producer.Return.Successes = true

	producer, err := sarama.NewAsyncProducer(cfg.KafkaBrokers, saramaCfg)
	if err != nil {
		log.Fatalf("failed to create Kafka producer: %v", err)
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	// Monitor async producer successes/errors
	go func() {
		for {
			select {
			case msg := <-producer.Successes():
				log.Debug("message delivered: topic=%s partition=%d offset=%d", msg.Topic, msg.Partition, msg.Offset)
			case err := <-producer.Errors():
				log.Printf("kafka produce error: %v", err)
			case <-ctx.Done():
				return
			}
		}
	}()

	// Tail the NDJSON file
	if err := tailFile(ctx, cfg, producer); err != nil {
		log.Fatalf("tail failed: %v", err)
	}

	<-sigCh
	log.Println("shutting down tailer")
	cancel()
	producer.AsyncClose()
	time.Sleep(1 * time.Second)
}

func tailFile(ctx context.Context, cfg config, producer sarama.AsyncProducer) error {
	file, err := os.Open(cfg.FilePath)
	if err != nil {
		// File may not exist yet, wait for it
		log.Printf("file %s not found, waiting...", cfg.FilePath)
		for {
			time.Sleep(1 * time.Second)
			file, err = os.Open(cfg.FilePath)
			if err == nil {
				break
			}
			select {
			case <-ctx.Done():
				return ctx.Err()
			default:
			}
		}
	}

	fileInfo, err := file.Stat()
	if err != nil {
		return fmt.Errorf("stat file: %w", err)
	}

	reader := bufio.NewReader(file)
	offset := fileInfo.Size()

	for {
		select {
		case <-ctx.Done():
			file.Close()
			return nil
		default:
		}

		line, err := reader.ReadString('\n')
		if err != nil {
			// No new data, poll again
			time.Sleep(cfg.PollInterval)

			newInfo, statErr := os.Stat(cfg.FilePath)
			if statErr != nil {
				continue
			}
			if newInfo.Size() < offset {
				// File was truncated
				file.Close()
				file, err = os.Open(cfg.FilePath)
				if err != nil {
					continue
				}
				reader = bufio.NewReader(file)
				offset = 0
				continue
			}
			if newInfo.Size() > offset {
				file.Seek(offset, 0)
				reader = bufio.NewReader(file)
				offset = newInfo.Size()
			}
			continue
		}

		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}

		var sample TrafficSample
		if err := json.Unmarshal([]byte(line), &sample); err != nil {
			log.Printf("failed to parse line: %v", err)
			continue
		}

		key := fmt.Sprintf("%s|%s", sample.Method, sample.Path)
		msg := &sarama.ProducerMessage{
			Topic: cfg.KafkaTopic,
			Key:   sarama.StringEncoder(key),
			Value: sarama.StringEncoder(line),
			Headers: []sarama.RecordHeader{
				{Key: []byte("service"), Value: []byte(sample.Source)},
				{Key: []byte("environment"), Value: []byte(sample.Environment)},
			},
		}

		producer.Input() <- msg
	}
}
