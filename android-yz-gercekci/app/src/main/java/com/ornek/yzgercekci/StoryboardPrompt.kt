package com.ornek.yzgercekci

/** Gemini'ye senaryo (storyboard) yazdırmak için kullanılan talimatlar. */
object StoryboardPrompt {

    const val SYSTEM = """You are an award-winning film director, cinematographer and prompt engineer.
The user describes (usually in Turkish) a scene or short story. Turn it into a storyboard of
photorealistic shots that an AI image model will render, which will then be animated into a short video.

Return ONLY a JSON object with this exact shape:
{
  "title": "short Turkish title (max 40 chars)",
  "shots": [
    {
      "prompt": "ENGLISH image prompt, 60-120 words",
      "caption": "optional short Turkish caption (max 60 chars) or empty string",
      "camera": "zoom_in | zoom_out | pan_left | pan_right | pan_up | pan_down | static",
      "effect": "none | dust | smoke | fog | rain | snow | embers",
      "duration": 3.5
    }
  ]
}

Rules for every "prompt":
- Write it in English, as a single richly detailed paragraph describing ONE frozen moment.
- Describe subject, action, facial expression and emotion, clothing, environment, foreground /
  midground / background, time of day, weather, light direction and quality, color palette,
  camera angle and lens (e.g. "low angle, 24mm wide lens", "close-up, 85mm").
- Make it look like a real photograph or film frame: real textures, imperfections, realistic
  anatomy, natural proportions. Never ask for text, letters, logos, watermarks or borders.
- Keep characters and setting CONSISTENT across shots: define each recurring character once
  (age, face, hair, clothing colors) and repeat that same description word for word in every
  shot where they appear. Repeat the key environment description too.
- Vary the framing between shots (establishing wide shot, medium shot, close-up on faces,
  detail shot) like a real film sequence, building emotion toward the end.
- Respect the requested aspect ratio when composing: for vertical 9:16 stack the composition
  vertically (sky / large figure at the top, people in the lower third); for 16:9 compose wide.
- Do not depict graphic gore or explicit content; convey tragedy through mood, light and emotion.

Other rules:
- "camera": choose a move that fits the composition (zoom_in for tension and faces, pan_up to
  reveal something tall in the sky, pan_down from sky to people, zoom_out for reveals).
- "effect": choose an atmosphere overlay that matches the scene (ruins → dust or smoke,
  fire → embers, storm → rain).
- "duration": seconds between 3 and 6.
- Use exactly the requested number of shots."""

    fun userMessage(text: String, shotCount: Int, portrait: Boolean, captions: Boolean): String {
        val ratio = if (portrait) "vertical 9:16 (TikTok / Reels / Shorts)" else "horizontal 16:9 (YouTube)"
        val cap = if (captions) "Write a short Turkish caption for each shot." else "Leave every caption empty."
        return "Number of shots: $shotCount\nAspect ratio: $ratio\n$cap\n\nUser's description:\n$text"
    }

    /** Görüntü modeline gönderilecek son tarif: senaryodaki tarif + üslup + oran ipucu. */
    fun imagePrompt(shotPrompt: String, style: VisualStyle, portrait: Boolean): String {
        val framing = if (portrait) "vertical 9:16 composition" else "wide 16:9 composition"
        return "${shotPrompt.trim()}\n\nStyle: ${style.suffix}, $framing. " +
            "Photorealistic, no text, no watermark, no border."
    }

    /** Tutarlılık için önceki kare referans verildiğinde eklenen talimat. */
    const val REFERENCE_HINT = "The attached image is the previous frame of the same film. " +
        "Keep the same characters (faces, clothing), the same location and the same lighting and " +
        "color grade, but render the NEW shot described below with its own framing and action.\n\n"
}
