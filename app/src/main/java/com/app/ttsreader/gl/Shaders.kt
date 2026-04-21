package com.app.ttsreader.gl

/**
 * All GLSL source code for the SDF overlay pipeline.
 *
 * ## Programs
 * - **Box program** (`QUAD_VERT` + `BOX_FRAG`): renders geometry quads —
 *   block fills, borders, dim overlays, glow halos, scan-line beams, and flat
 *   reticle lines.  The [MODE_*] float constants select the fragment behaviour.
 * - **Glyph program** (`QUAD_VERT` + `GLYPH_FRAG`): renders individual
 *   character quads by sampling the SDF atlas.  Produces crisp, anti-aliased
 *   neon-green text at any scale.
 *
 * ## Box shader modes ([u_Mode])
 * | Value | Constant       | Description                                      |
 * |-------|----------------|--------------------------------------------------|
 * | 0.0   | [MODE_AR_FILL] | AR Lens: neon-green fill + pulsing border        |
 * | 1.0   | [MODE_IX_FILL] | Indexing: score-heatmap fill + pulsing border    |
 * | 2.0   | [MODE_DIM]     | Full-screen semi-transparent black dim overlay   |
 * | 3.0   | [MODE_GLOW]    | Radial neon-green glow halo behind boxes         |
 * | 4.0   | [MODE_SCAN]    | Full-screen animated scan-line beam (u_Time–driven) |
 * | 5.0   | [MODE_FLAT]    | Flat solid neon green (corner reticles)          |
 *
 * ## SDF encoding ([GLYPH_FRAG])
 * - **0.0** (alpha=0)   → far outside glyph
 * - **0.5** (alpha=127) → glyph edge
 * - **1.0** (alpha=255) → far inside glyph
 * `smoothstep(0.47, 0.53, sdf)` resolves the edge with sub-pixel sharpness.
 */
object Shaders {

    // ── Mode constants (pass as u_Mode) ───────────────────────────────────────

    const val MODE_AR_FILL = 0f   // AR Lens block fill + pulsing neon border
    const val MODE_IX_FILL = 1f   // Indexing heatmap fill + pulsing neon border
    const val MODE_DIM     = 2f   // Semi-transparent black dim overlay
    const val MODE_GLOW    = 3f   // Radial neon green glow halo
    const val MODE_SCAN    = 4f   // Animated full-screen scan-line beam
    const val MODE_FLAT    = 5f   // Flat solid neon green (reticle lines)

    // ── Shared vertex shader ──────────────────────────────────────────────────

    val QUAD_VERT = """
        attribute vec2 a_Position;
        attribute vec2 a_TexCoord;
        varying   vec2 v_TexCoord;
        void main() {
            gl_Position = vec4(a_Position, 0.0, 1.0);
            v_TexCoord  = a_TexCoord;
        }
    """.trimIndent()

    // ── Box / chrome fragment shader ──────────────────────────────────────────

    /**
     * Multi-mode fragment shader for all non-text geometry.
     *
     * Uniforms:
     * - `u_Mode`      — selects rendering mode (see [MODE_*] constants)
     * - `u_Alpha`     — master opacity for this quad
     * - `u_Time`      — seconds since GL surface creation (for animations)
     * - `u_Score`     — match confidence [0,1] for [MODE_IX_FILL] heatmap
     * - `u_BorderFrac`— border thickness as fraction of UV space [0,1]
     *
     * All output is pre-multiplied alpha so the blend equation
     * `(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)` composites correctly.
     */
    val BOX_FRAG = """
        precision mediump float;

        uniform float u_Mode;
        uniform float u_Alpha;
        uniform float u_Time;
        uniform float u_Score;
        uniform float u_BorderFrac;

        varying vec2 v_TexCoord;

        const vec3 kNeon = vec3(0.224, 1.0, 0.078);   // #39FF14

        void main() {
            vec2  uv    = v_TexCoord;
            float pulse = 0.60 + 0.40 * sin(u_Time * 5.0);

            if (u_Mode < 0.5) {
                // ── MODE 0: AR Lens fill + pulsing neon border ────────────────
                bool border = uv.x < u_BorderFrac || uv.x > 1.0 - u_BorderFrac ||
                              uv.y < u_BorderFrac || uv.y > 1.0 - u_BorderFrac;
                float a = border ? u_Alpha * pulse : u_Alpha * 0.18;
                gl_FragColor = vec4(kNeon * a, a);

            } else if (u_Mode < 1.5) {
                // ── MODE 1: Indexing heatmap fill + pulsing border ────────────
                // Low score  → cool green;  high score → full neon
                vec3  heat   = mix(vec3(0.05, 0.72, 0.28), kNeon, clamp(u_Score, 0.0, 1.0));
                bool  border = uv.x < u_BorderFrac || uv.x > 1.0 - u_BorderFrac ||
                               uv.y < u_BorderFrac || uv.y > 1.0 - u_BorderFrac;
                float a = border ? pulse : 0.22;
                gl_FragColor = vec4(heat * a, a);

            } else if (u_Mode < 2.5) {
                // ── MODE 2: Black dim overlay ─────────────────────────────────
                float a = u_Alpha;
                gl_FragColor = vec4(0.0, 0.0, 0.0, a);

            } else if (u_Mode < 3.5) {
                // ── MODE 3: Radial neon glow halo ─────────────────────────────
                float dist = length(uv - vec2(0.5));
                float glow = max(0.0, 1.0 - dist * 2.0) * u_Alpha;
                // Outer breath: slow pulse at half intensity
                float breath = 0.80 + 0.20 * sin(u_Time * 2.0);
                glow *= breath;
                gl_FragColor = vec4(kNeon * glow, glow);

            } else if (u_Mode < 4.5) {
                // ── MODE 4: Animated scan-line beam ───────────────────────────
                // Scan position sweeps 0→1→0 in screen-UV Y space
                float scanY  = 0.5 + 0.5 * sin(u_Time * 1.6);
                float dist   = abs(uv.y - scanY);
                float core   = 1.0 - smoothstep(0.000, 0.018, dist);  // sharp core
                float midGlow = 1.0 - smoothstep(0.000, 0.080, dist);  // mid glow
                float wide   = 1.0 - smoothstep(0.000, 0.260, dist);  // wide aura
                float bright = core * 0.90 + midGlow * 0.35 + wide * 0.10;
                float a      = bright * u_Alpha;
                gl_FragColor = vec4(kNeon * a, a);

            } else {
                // ── MODE 5: Flat neon green (corner reticles, etc.) ───────────
                float a = u_Alpha;
                gl_FragColor = vec4(kNeon * a, a);
            }
        }
    """.trimIndent()

    // ── SDF glyph fragment shader ─────────────────────────────────────────────

    /**
     * Samples the SDF atlas and renders anti-aliased neon-green text.
     *
     * SDF edge at 0.5: `smoothstep(0.47, 0.53)` gives ~1 px soft edge at a
     * typical text size.  A secondary outer halo (`smoothstep(0.33, 0.47)`)
     * adds a subtle white bloom that lifts the text off dark overlays.
     *
     * Output is pre-multiplied alpha.
     *
     * Uniforms:
     * - `u_Atlas` — sampler2D of the 1024×1024 GL_ALPHA SDF atlas texture
     * - `u_Alpha` — block's [ArLensBlock.displayAlpha] for fade-in/out
     */
    val GLYPH_FRAG = """
        precision mediump float;

        uniform sampler2D u_Atlas;
        uniform float     u_Alpha;

        varying vec2 v_TexCoord;

        const vec3 kNeon = vec3(0.224, 1.0, 0.078);

        void main() {
            float sdf   = texture2D(u_Atlas, v_TexCoord).a;
            float core  = smoothstep(0.47, 0.53, sdf);
            float halo  = smoothstep(0.33, 0.47, sdf) * (1.0 - core);
            // Neon green core + soft white-tinted outer halo
            vec3  color = kNeon * core + vec3(0.70, 1.0, 0.70) * halo;
            float alpha = (core + halo * 0.45) * u_Alpha;
            gl_FragColor = vec4(color * alpha, alpha);   // pre-multiplied
        }
    """.trimIndent()
}
