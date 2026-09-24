package com.movie.viewmodel;

/**
 * Java port of the Kotlin sealed interface:
 *
 *   sealed interface ImportingState {
 *       object Idle : ImportingState
 *       object Loading : ImportingState
 *       data class Success(val count: Int) : ImportingState
 *       data class Error(val message: String) : ImportingState
 *   }
 *
 * Modelled as a single immutable class with a type discriminator plus static factories.
 */
public final class ImportingState {

    public enum Type {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    private final Type type;
    private final int count;
    private final String message;

    private ImportingState(Type type, int count, String message) {
        this.type = type;
        this.count = count;
        this.message = message;
    }

    public static ImportingState idle() {
        return new ImportingState(Type.IDLE, 0, null);
    }

    public static ImportingState loading() {
        return new ImportingState(Type.LOADING, 0, null);
    }

    public static ImportingState success(int count) {
        return new ImportingState(Type.SUCCESS, count, null);
    }

    public static ImportingState error(String message) {
        return new ImportingState(Type.ERROR, 0, message);
    }

    public Type getType() {
        return type;
    }

    public int getCount() {
        return count;
    }

    public String getMessage() {
        return message;
    }

    public boolean isIdle() {
        return type == Type.IDLE;
    }

    public boolean isLoading() {
        return type == Type.LOADING;
    }

    public boolean isSuccess() {
        return type == Type.SUCCESS;
    }

    public boolean isError() {
        return type == Type.ERROR;
    }

    @Override
    public String toString() {
        switch (type) {
            case SUCCESS:
                return "ImportingState.Success(count=" + count + ")";
            case ERROR:
                return "ImportingState.Error(message=" + message + ")";
            case LOADING:
                return "ImportingState.Loading";
            default:
                return "ImportingState.Idle";
        }
    }
}
