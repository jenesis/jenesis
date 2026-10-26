package build.jenesis;

import module java.base;

public interface Permits {

    void acquire() throws InterruptedException;

    void release();

    static Permits of(int count) {
        return new Counted(new Semaphore(count));
    }

    static Permits compound(Permits... permits) {
        List<Permits> given = Stream.of(permits).filter(Objects::nonNull).toList();
        return switch (given.size()) {
            case 0 -> null;
            case 1 -> given.getFirst();
            default -> new Compound(given);
        };
    }

    record Counted(Semaphore semaphore) implements Permits {

        @Override
        public void acquire() throws InterruptedException {
            semaphore.acquire();
        }

        @Override
        public void release() {
            semaphore.release();
        }
    }

    record Compound(List<Permits> permits) implements Permits {

        public Compound {
            permits = List.copyOf(permits);
        }

        @Override
        public void acquire() throws InterruptedException {
            for (int index = 0; index < permits.size(); index++) {
                try {
                    permits.get(index).acquire();
                } catch (InterruptedException | RuntimeException e) {
                    for (Permits acquired : permits.subList(0, index).reversed()) {
                        acquired.release();
                    }
                    throw e;
                }
            }
        }

        @Override
        public void release() {
            RuntimeException failure = null;
            for (Permits permit : permits.reversed()) {
                try {
                    permit.release();
                } catch (RuntimeException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
