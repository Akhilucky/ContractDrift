import logging
import os
from functools import wraps

import grpc
import jwt

logger = logging.getLogger(__name__)

JWT_SECRET = os.environ.get("JWT_SECRET", "change-me-in-production-at-least-32-chars!!")
ALGORITHM = "HS256"


def extract_token(context: grpc.ServicerContext) -> str:
    """Extract JWT token from gRPC metadata."""
    metadata = dict(context.invocation_metadata())
    auth_header = metadata.get("authorization", "")
    if auth_header.startswith("Bearer "):
        return auth_header[7:]
    return None


def validate_token(token: str) -> dict:
    """Validate JWT token and return claims."""
    try:
        claims = jwt.decode(token, JWT_SECRET, algorithms=[ALGORITHM])
        return claims
    except jwt.ExpiredSignatureError:
        logger.warning("Token expired")
        return None
    except jwt.InvalidTokenError as e:
        logger.warning("Invalid token: %s", e)
        return None


def require_auth(func):
    """Decorator to require authentication for gRPC methods."""
    @wraps(func)
    def wrapper(self, request, context):
        token = extract_token(context)
        if not token:
            context.abort(grpc.StatusCode.UNAUTHENTICATED, "Missing or invalid authorization header")
            return None

        claims = validate_token(token)
        if not claims:
            context.abort(grpc.StatusCode.UNAUTHENTICATED, "Invalid or expired token")
            return None

        context.claims = claims
        return func(self, request, context)
    return wrapper


class AuthInterceptor:
    """gRPC interceptor that enforces JWT authentication."""

    def __init__(self):
        self.public_methods = set()

    def add_public_method(self, method_name: str):
        """Add a method that doesn't require authentication."""
        self.public_methods.add(method_name)

    def intercept_service(self, continuation, handler_call_details):
        method_name = handler_call_details.method

        if method_name in self.public_methods:
            return continuation(handler_call_details)

        return _wrapped_handler(continuation, handler_call_details)


def _wrapped_handler(continuation, handler_call_details):
    """Wrap handler to add authentication check."""

    async def _check_auth(context):
        token = extract_token(context)
        if not token:
            context.abort(grpc.StatusCode.UNAUTHENTICATED, "Missing or invalid authorization header")
            return None

        claims = validate_token(token)
        if not claims:
            context.abort(grpc.StatusCode.UNAUTHENTICATED, "Invalid or expired token")
            return None

        context.claims = claims
        return await continuation(handler_call_details)

    return _check_auth
