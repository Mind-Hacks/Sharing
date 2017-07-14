package ThreadMessage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class SyncAccTest {
    private final static int TOTAL = 500000000;

    private interface SyncAcc {

        /**
         * 执行自增逻辑
         */
        void acc();

        /**
         * 返回最终结果
         *
         * @return
         */
        int result();

    }

    private static class SyncTemplate {
        //测试用例名称
        private final String name;

        //线程数
        private final int threads;

        //自增逻辑
        private final SyncAcc syncAcc;

        public SyncTemplate(String name, int threads, SyncAcc syncAcc) {
            this.name = name;
            this.threads = threads;
            this.syncAcc = syncAcc;
        }

        public void execute() {
            //做两扇门
            final CountDownLatch begin = new CountDownLatch(1);
            final CountDownLatch end = new CountDownLatch(threads);
            //线程池
            ExecutorService thradPool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
                private AtomicInteger atomicInteger = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("sync-thread-" + atomicInteger.getAndIncrement());
                    return thread;
                }
            });
            //开始分工
            final int perCount = TOTAL / threads;
            for (int i = 0; i < threads; i++) {
                thradPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // 等待开始信号
                            begin.await();
                            for (int c = 0; c < perCount; c++) {
                                //执行自增逻辑
                                syncAcc.acc();
                            }
                            //System.out.println(Thread.currentThread().getName() + "-> Done!");
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            //报告完成
                            end.countDown();
                        }
                    }
                });
            }

            //线程们开始执行
            begin.countDown();
            long startTime = System.currentTimeMillis();
            try {
                end.await();
            } catch (Exception e) {
                e.printStackTrace();
            }
            long endTime = System.currentTimeMillis();
            System.out.println(name + " cost: " + (endTime - startTime) + "ms result=" + syncAcc.result());
        }

    }

    /**
     * 检测不同自增实现的开销
     *
     * @param args
     */
    public static void main(String[] args) {
        // 测试用例-1
        String name = "单线程自增";
        int threads = 1;
        new SyncTemplate(name, threads, new SyncAcc() {
            private int foo;

            @Override
            public void acc() {
                foo++;
            }

            @Override
            public int result() {
                return foo;
            }
        }).execute();

        threads = 2;
        name = threads +"-线程volatile自增";
        new SyncTemplate(name, threads, new SyncAcc() {
            private volatile int foo;

            @Override
            public void acc() {
                foo++;		//呵呵呵
            }

            @Override
            public int result() {
                return foo;
            }
        }).execute();

        name = "单线程cas自增";
        threads = 1;
        new SyncTemplate(name, threads, new SyncAcc() {
            private AtomicInteger foo = new AtomicInteger(0);

            @Override
            public void acc() {
                foo.getAndIncrement();
            }

            @Override
            public int result() {
                return foo.get();
            }
        }).execute();

        name = "2线程cas自增";
        threads = 2;
        new SyncTemplate(name, threads, new SyncAcc() {
            private AtomicInteger foo = new AtomicInteger(0);

            @Override
            public void acc() {
                foo.getAndIncrement();
            }

            @Override
            public int result() {
                return foo.get();
            }
        }).execute();

        name = "10线程cas自增";
        threads = 10;
        new SyncTemplate(name, threads, new SyncAcc() {
            private AtomicInteger foo = new AtomicInteger(0);

            @Override
            public void acc() {
                foo.getAndIncrement();
            }

            @Override
            public int result() {
                return foo.get();
            }
        }).execute();

        name = "1-内置锁单线程自增";
        threads = 1;
        new SyncTemplate(name, threads, new SyncAcc() {
            private int foo;

            private Object obj = new Object();

            @Override
            public void acc() {
                synchronized (obj) {
                    foo++;
                }
            }

            @Override
            public int result() {
                synchronized (obj) {
                    return foo;
                }
            }
        }).execute();

        threads = 2;
        name = threads + "-内置锁单线程自增";
        new SyncTemplate(name, threads, new SyncAcc() {
            private int foo;

            private Object obj = new Object();

            @Override
            public void acc() {
                synchronized (obj) {
                    foo++;
                }
            }

            @Override
            public int result() {
                synchronized (obj) {
                    return foo;
                }
            }
        }).execute();

        threads = 10;
        name = threads + "-内置锁单线程自增";
        new SyncTemplate(name, threads, new SyncAcc() {
            private int foo;

            private Object obj = new Object();

            @Override
            public void acc() {
                synchronized (obj) {
                    foo++;
                }
            }

            @Override
            public int result() {
                synchronized (obj) {
                    return foo;
                }
            }
        }).execute();
    }
}
