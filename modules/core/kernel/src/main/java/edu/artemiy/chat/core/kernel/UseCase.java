package edu.artemiy.chat.core.kernel;

@FunctionalInterface
public interface UseCase<I, O> {

    O handle(I input);
}
