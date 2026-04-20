package edu.artemiy.chat.core.kernel;

public sealed interface Result<T> permits Result.Success, Result.Failure {

    record Success<T>(T value) implements Result<T> {
    }

    record Failure<T>(ApplicationError error) implements Result<T> {
    }
}
