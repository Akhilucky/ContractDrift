import { useEffect, useRef } from 'react'
import { sentinelWs } from '../lib/websocket'

type MessageHandler = (data: unknown) => void

export function useWebSocket<T>(event: string, handler: (data: T) => void) {
  const handlerRef = useRef(handler)
  handlerRef.current = handler

  useEffect(() => {
    const wrapped: MessageHandler = (data) => handlerRef.current(data as T)
    sentinelWs.on(event, wrapped)
    return () => sentinelWs.off(event, wrapped)
  }, [event])
}
