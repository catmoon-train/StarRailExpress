package org.agmas.noellesroles.voice;

/**
 * Phase Vocoder implementation for pitch shifting audio in real-time.
 * Based on the WSOLA (Waveform Similarity Overlap-Add) algorithm.
 */
@SuppressWarnings("unused")
public class HeliumPitchShifter {

    private static final int WINDOW = 960;
    private static final int HOP = 240;
    private static final int OVERLAP_LEN = 720;
    private static final int SEARCH_RADIUS = 480;
    private static final int CORR_WIN = 480;
    private static final float HANN_GAIN = 2.0F;
    private static final float[] HANN = buildHann(960);
    private static final float[] CORR_WEIGHTS = buildCorrWeights(480);

    private static final int IN_RING = 32768;
    private static final int IN_MASK = 32767;
    private final float[] inRing = new float[32768];
    private long inWriteCount = 0L;

    private static final int STRETCH_RING = 16384;
    private static final int STRETCH_MASK = 16383;
    private final float[] stretchRing = new float[16384];

    private long synthFrames = 0L;
    private double anaPosD = 0.0D;
    private final float[] prevTailRaw = new float[480];

    private double resamplePos = 0.0D;
    private long zeroedThrough = 0L;

    private static final int MAX_LATENCY = 3072;
    private int latencyCount = 0;

    /**
     * Process audio samples with pitch shifting.
     * 
     * @param in    Input PCM audio data (16-bit signed)
     * @param ratio Pitch ratio (1.0 = normal, >1.0 = higher pitch, <1.0 = lower
     *              pitch)
     * @return Pitch-shifted PCM audio data
     */
    public short[] process(short[] in, float ratio) {
        ratio = Math.max(0.5F, Math.min(ratio, 2.5F));
        short[] out = new short[in.length];

        for (int i = 0; i < in.length; i++) {
            this.inRing[(int) (this.inWriteCount & IN_MASK)] = in[i] * 3.0517578E-5F;
            this.inWriteCount++;

            while (canSynth()) {
                runSynthFrame(ratio);
            }

            if (this.latencyCount < MAX_LATENCY) {
                this.latencyCount++;
                out[i] = 0;
            } else {
                long safeSynthPos = this.synthFrames * 240L - 720L;

                if (this.resamplePos + 2.0D < safeSynthPos) {
                    float s = hermite4(this.resamplePos) / 2.0F;
                    out[i] = toShort(s);
                    this.resamplePos += ratio;

                    while (this.zeroedThrough + 2L < (long) this.resamplePos) {
                        this.stretchRing[(int) (this.zeroedThrough & STRETCH_MASK)] = 0.0F;
                        this.zeroedThrough++;
                    }
                } else {
                    out[i] = 0;
                }
            }
        }
        return out;
    }

    private boolean canSynth() {
        return ((long) this.anaPosD + WINDOW + SEARCH_RADIUS <= this.inWriteCount);
    }

    private void runSynthFrame(float ratio) {
        long naive = (long) this.anaPosD;
        long bestA = naive;

        if (this.synthFrames > 0L) {
            float bestScore = Float.MAX_VALUE;
            for (int d = -SEARCH_RADIUS; d <= SEARCH_RADIUS; d++) {
                long cand = naive + d;

                if (cand >= 0L && cand + WINDOW <= this.inWriteCount && this.inWriteCount - cand <= IN_RING) {
                    float score = 0.0F;
                    for (int i = 0; i < CORR_WIN; i++) {
                        float diff = this.inRing[(int) (cand + i & IN_MASK)] - this.prevTailRaw[i];
                        score += diff * diff * CORR_WEIGHTS[i];
                        if (score >= bestScore)
                            break;
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        bestA = cand;
                    }
                }
            }
        }

        long synthPos = this.synthFrames * 240L;
        for (int k = 0; k < WINDOW; k++) {
            this.stretchRing[(int) (synthPos + k
                    & STRETCH_MASK)] = this.stretchRing[(int) (synthPos + k & STRETCH_MASK)] +
                            this.inRing[(int) (bestA + k & IN_MASK)] * HANN[k];
        }

        for (int k = 0; k < CORR_WIN; k++) {
            this.prevTailRaw[k] = this.inRing[(int) (bestA + 240L + k & IN_MASK)];
        }

        this.anaPosD += 240.0D / ratio;
        this.synthFrames++;
    }

    private float hermite4(double pos) {
        long base = (long) pos;
        float t = (float) (pos - base);

        float y0 = this.stretchRing[(int) (base - 1L & STRETCH_MASK)];
        float y1 = this.stretchRing[(int) (base & STRETCH_MASK)];
        float y2 = this.stretchRing[(int) (base + 1L & STRETCH_MASK)];
        float y3 = this.stretchRing[(int) (base + 2L & STRETCH_MASK)];

        float c0 = y1;
        float c1 = 0.5F * (y2 - y0);
        float c2 = y0 - 2.5F * y1 + 2.0F * y2 - 0.5F * y3;
        float c3 = 0.5F * (y3 - y0) + 1.5F * (y1 - y2);
        return ((c3 * t + c2) * t + c1) * t + c0;
    }

    private static short toShort(float v) {
        float s = v * 32767.0F;
        if (s > 32767.0F)
            return Short.MAX_VALUE;
        if (s < -32768.0F)
            return Short.MIN_VALUE;
        return (short) (int) s;
    }

    private static float[] buildHann(int n) {
        float[] w = new float[n];
        double c = 6.283185307179586D / n;
        for (int k = 0; k < n; k++) {
            w[k] = (float) (0.5D - 0.5D * Math.cos(c * k));
        }
        return w;
    }

    private static float[] buildCorrWeights(int n) {
        float[] w = new float[n];
        double c = Math.PI / n;
        for (int k = 0; k < n; k++) {
            w[k] = (float) Math.sin(c * (k + 0.5D));
        }
        return w;
    }
}
