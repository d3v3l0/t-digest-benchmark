package com.tdunning;

import com.tdunning.math.stats.AVLTreeDigest;
import com.tdunning.math.stats.MergingDigest;
import com.tdunning.math.stats.TDigest;
import org.apache.mahout.math.jet.random.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@State(Scope.Thread)
public class TDigestBench {

    public enum TDigestFactory {
        MERGE {
            @Override
            TDigest create(double compression) {
                return new MergingDigest(compression, (int) (10 * compression));
            }
        },
        AVL_TREE {
            @Override
            TDigest create(double compression) {
                return new AVLTreeDigest(compression);
            }
        };

        abstract TDigest create(double compression);
    }

    public enum DistributionFactory {
        UNIFORM {
            @Override
            AbstractDistribution create(Random random) {
                return new Uniform(0, 1, random);
            }
        },
        SEQUENTIAL {
            @Override
            AbstractDistribution create(Random random) {
                return new AbstractContinousDistribution() {
                    double base = 0;

                    @Override
                    public double nextDouble() {
                        base += Math.PI * 1e-5;
                        return base;
                    }
                };
            }
        },
        REPEATED {
            @Override
            AbstractDistribution create(final Random random) {
                return new AbstractContinousDistribution() {
                    @Override
                    public double nextDouble() {
                        return random.nextInt(10);
                    }
                };
            }
        },
        GAMMA {
            @Override
            AbstractDistribution create(Random random) {
                return new Gamma(0.1, 0.1, random);
            }
        },
        NORMAL {
            @Override
            AbstractDistribution create(Random random) {
                return new Normal(0.1, 0.1, random);
            }
        };

        abstract AbstractDistribution create(Random random);
    }

    @Param({"100", "300"})
    double compression;

    @Param({"MERGE", "AVL_TREE"})
    TDigestFactory tdigestFactory;

    @Param({"NORMAL", "GAMMA"})
    DistributionFactory distributionFactory;

    Random random;
    TDigest tdigest;
    AbstractDistribution distribution;

    double[] data = new double[1000000];

    @Setup
    public void setUp() {
        random = ThreadLocalRandom.current();
        tdigest = tdigestFactory.create(compression);
        distribution = distributionFactory.create(random);
        // first values are cheap to add, so pre-fill the t-digest to have more realistic results
        for (int i = 0; i < 10000; ++i) {
            tdigest.add(distribution.nextDouble());
        }

        for (int i = 0; i < data.length; ++i) {
            data[i] = distribution.nextDouble();
        }
    }

    @State(Scope.Thread)
    public static class ThreadState {
        int index = 0;
    }

    @Benchmark
    public void timeAdd(MergeBench.ThreadState state) {
        if (state.index >= data.length) {
            state.index = 0;
        }
        tdigest.add(data[state.index++]);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + TDigestBench.class.getSimpleName() + ".*")
                .resultFormat(ResultFormatType.CSV)
                .result("overall-results.csv")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(opt).run();
    }
}
