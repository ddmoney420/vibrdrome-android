package com.vibrdrome.app.visualizer

data class ShaderPreset(
    val name: String,
    val fragmentShader: String,
)

object ShaderPresets {

    private const val COMMON_HEADER = """
        precision mediump float;
        uniform float uTime;
        uniform float uEnergy;
        uniform float uBass;
        uniform float uMid;
        uniform float uTreble;
        uniform vec2 uResolution;
        uniform sampler2D uWaveform;

        #define PI 3.14159265359
    """

    val plasma = ShaderPreset("Plasma", COMMON_HEADER + """
        void main() {
            vec2 uv = gl_FragCoord.xy / uResolution;
            vec2 p = uv * 2.0 - 1.0;
            p.x *= uResolution.x / uResolution.y;

            float t = uTime * 0.5 + uEnergy * 2.0;
            float v = 0.0;
            v += sin(p.x * 3.0 + t);
            v += sin((p.y * 3.0 + t) * 0.7);
            v += sin((p.x * 3.0 + p.y * 3.0 + t) * 0.5);
            v += sin(length(p) * 5.0 - t * 2.0) * uBass;
            v *= 0.5;

            vec3 col = vec3(
                sin(v * PI + uBass * 3.0) * 0.5 + 0.5,
                sin(v * PI + 2.094 + uMid * 3.0) * 0.5 + 0.5,
                sin(v * PI + 4.188 + uTreble * 3.0) * 0.5 + 0.5
            );
            col *= 0.7 + uEnergy * 0.5;
            gl_FragColor = vec4(col, 1.0);
        }
    """)

    val kaleidoscope = ShaderPreset("Kaleidoscope", COMMON_HEADER + """
        void main() {
            vec2 uv = gl_FragCoord.xy / uResolution;
            vec2 p = uv * 2.0 - 1.0;
            p.x *= uResolution.x / uResolution.y;

            float a = atan(p.y, p.x);
            float r = length(p);

            float segments = 6.0 + uBass * 4.0;
            a = mod(a, 2.0 * PI / segments);
            a = abs(a - PI / segments);

            vec2 mp = vec2(cos(a), sin(a)) * r;
            float t = uTime * 0.3;

            float v = 0.0;
            v += sin(mp.x * 8.0 + t) * 0.5;
            v += sin(mp.y * 6.0 - t * 1.3) * 0.5;
            v += sin((mp.x + mp.y) * 4.0 + t * 0.7) * uMid;
            v += sin(r * 12.0 - t * 3.0) * uBass * 0.5;

            vec3 col = vec3(
                sin(v * 3.0 + t) * 0.5 + 0.5,
                sin(v * 3.0 + t + 2.094) * 0.5 + 0.5,
                sin(v * 3.0 + t + 4.188) * 0.5 + 0.5
            );
            col *= smoothstep(1.5, 0.2, r);
            col *= 0.6 + uEnergy * 0.6;
            gl_FragColor = vec4(col, 1.0);
        }
    """)

    val tunnel = ShaderPreset("Tunnel", COMMON_HEADER + """
        void main() {
            vec2 uv = gl_FragCoord.xy / uResolution;
            vec2 p = uv * 2.0 - 1.0;
            p.x *= uResolution.x / uResolution.y;

            float a = atan(p.y, p.x) / PI;
            float r = 1.0 / (length(p) + 0.001);

            float t = uTime * 0.5;
            vec2 tc = vec2(a + t * 0.1, r + t * (0.5 + uBass * 0.5));

            float v = sin(tc.x * 8.0 * PI) * sin(tc.y * 4.0);
            v += sin(tc.x * 4.0 * PI + uTime) * uMid;

            float pulse = sin(r * 2.0 - uTime * 4.0) * uBass;

            vec3 col = vec3(
                0.2 + v * 0.3 + pulse * 0.4,
                0.1 + v * 0.2 + uMid * 0.3,
                0.4 + v * 0.4 + uTreble * 0.3
            );
            col *= r * 0.15;
            col = clamp(col, 0.0, 1.0);
            col *= 0.5 + uEnergy * 0.8;
            gl_FragColor = vec4(col, 1.0);
        }
    """)

    val fractalPulse = ShaderPreset("Fractal Pulse", COMMON_HEADER + """
        void main() {
            vec2 uv = gl_FragCoord.xy / uResolution;
            vec2 c = uv * 3.0 - 1.5;
            c.x *= uResolution.x / uResolution.y;

            c += vec2(sin(uTime * 0.2) * 0.3, cos(uTime * 0.15) * 0.3);
            c *= 1.0 + uBass * 0.5;

            vec2 z = vec2(0.0);
            float iter = 0.0;
            for (int i = 0; i < 64; i++) {
                z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + c;
                if (dot(z, z) > 4.0) break;
                iter += 1.0;
            }
            iter /= 64.0;
            iter = pow(iter, 0.5);

            float t = uTime * 0.3;
            vec3 col = vec3(
                sin(iter * 10.0 + t) * 0.5 + 0.5,
                sin(iter * 10.0 + t + 2.0) * 0.5 + 0.5,
                sin(iter * 10.0 + t + 4.0) * 0.5 + 0.5
            );
            col *= iter * 2.0;
            col *= 0.5 + uEnergy * 0.8;
            gl_FragColor = vec4(col, 1.0);
        }
    """)

    val nebula = ShaderPreset("Nebula", COMMON_HEADER + """
        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
        }

        float noise(vec2 p) {
            vec2 i = floor(p);
            vec2 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float a = hash(i);
            float b = hash(i + vec2(1.0, 0.0));
            float c = hash(i + vec2(0.0, 1.0));
            float d = hash(i + vec2(1.0, 1.0));
            return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
        }

        float fbm(vec2 p) {
            float v = 0.0;
            float a = 0.5;
            for (int i = 0; i < 5; i++) {
                v += a * noise(p);
                p *= 2.0;
                a *= 0.5;
            }
            return v;
        }

        void main() {
            vec2 uv = gl_FragCoord.xy / uResolution;
            vec2 p = uv * 2.0 - 1.0;
            p.x *= uResolution.x / uResolution.y;

            float t = uTime * 0.1;
            float n1 = fbm(p * 3.0 + t + uBass);
            float n2 = fbm(p * 2.0 - t * 0.7 + n1 + uMid);
            float n3 = fbm(p * 4.0 + t * 0.3 + n2);

            vec3 col = vec3(0.0);
            col += vec3(0.4, 0.1, 0.6) * n1;
            col += vec3(0.1, 0.3, 0.7) * n2;
            col += vec3(0.6, 0.2, 0.4) * n3 * uTreble;

            float stars = step(0.98, hash(floor(uv * 200.0)));
            col += stars * (0.5 + uTreble * 0.5);

            col *= 0.6 + uEnergy * 0.6;
            gl_FragColor = vec4(col, 1.0);
        }
    """)

    val warpSpeed = ShaderPreset("Warp Speed", COMMON_HEADER + """
        void main() {
            vec2 uv = gl_FragCoord.xy / uResolution;
            vec2 p = uv - 0.5;
            p.x *= uResolution.x / uResolution.y;

            float t = uTime * (0.5 + uBass * 1.5);
            float a = atan(p.y, p.x);
            float r = length(p);

            float streak = 0.0;
            for (int i = 0; i < 20; i++) {
                float fi = float(i) / 20.0;
                float angle = fi * 2.0 * PI + t * 0.5;
                float dist = abs(a - angle);
                dist = min(dist, 2.0 * PI - dist);
                float brightness = smoothstep(0.15, 0.0, dist);
                brightness *= smoothstep(0.0, 0.3 + uEnergy * 0.2, r);
                brightness *= 1.0 - smoothstep(0.3, 0.5, r);
                streak += brightness * (0.5 + sin(fi * 30.0 + t * 3.0) * 0.5);
            }

            float ring = abs(r - 0.2 - uBass * 0.1);
            ring = smoothstep(0.02, 0.0, ring);

            vec3 col = vec3(0.3, 0.5, 1.0) * streak;
            col += vec3(0.8, 0.4, 1.0) * ring;
            col += vec3(1.0, 0.8, 0.3) * smoothstep(0.1, 0.0, r) * uEnergy;
            col *= 0.7 + uEnergy * 0.5;

            gl_FragColor = vec4(col, 1.0);
        }
    """)

    val allPresets = listOf(plasma, kaleidoscope, tunnel, fractalPulse, nebula, warpSpeed)
}
