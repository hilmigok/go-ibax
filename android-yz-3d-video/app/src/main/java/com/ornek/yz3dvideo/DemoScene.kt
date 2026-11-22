package com.ornek.yz3dvideo

/** İnternet veya API anahtarı olmadan da uygulamayı denemek için hazır sahne. */
object DemoScene {
    val JSON = """
{
  "title": "Yolda giden araba",
  "duration": 8,
  "sky": {"top": "#3A7BD5", "bottom": "#BEE3F8"},
  "ground": {"color": "#5DAE5B", "visible": true},
  "light": {"direction": [-0.4, -1, -0.3], "ambient": 0.35},
  "camera": {
    "start": {"position": [0, 4, 14], "target": [0, 1, 0]},
    "end": {"position": [10, 3, 9], "target": [2, 1, 0]}
  },
  "objects": [
    {"id": "road", "position": [0, 0.05, 0],
     "parts": [{"shape": "plane", "color": "#555555", "position": [0, 0, 0], "scale": [40, 1, 4]}]},
    {"id": "car", "position": [-12, 0, 0],
     "parts": [
       {"shape": "cube", "color": "#D32F2F", "position": [0, 0.7, 0], "scale": [3, 0.8, 1.5]},
       {"shape": "cube", "color": "#E57373", "position": [-0.2, 1.4, 0], "scale": [1.6, 0.7, 1.3]},
       {"shape": "cylinder", "color": "#212121", "position": [-1, 0.35, 0.8], "scale": [0.7, 0.3, 0.7], "rotation": [90, 0, 0]},
       {"shape": "cylinder", "color": "#212121", "position": [1, 0.35, 0.8], "scale": [0.7, 0.3, 0.7], "rotation": [90, 0, 0]},
       {"shape": "cylinder", "color": "#212121", "position": [-1, 0.35, -0.8], "scale": [0.7, 0.3, 0.7], "rotation": [90, 0, 0]},
       {"shape": "cylinder", "color": "#212121", "position": [1, 0.35, -0.8], "scale": [0.7, 0.3, 0.7], "rotation": [90, 0, 0]}
     ],
     "animations": [{"type": "move", "to": [12, 0, 0], "start": 0, "end": 8}]},
    {"id": "tree", "position": [-4, 0, -4],
     "parts": [
       {"shape": "cylinder", "color": "#795548", "position": [0, 1, 0], "scale": [0.4, 2, 0.4]},
       {"shape": "cone", "color": "#2E7D32", "position": [0, 3, 0], "scale": [2, 2.5, 2]}
     ]},
    {"id": "sun", "position": [-8, 12, -20],
     "parts": [{"shape": "sphere", "color": "#FFD54F", "position": [0, 0, 0], "scale": [3, 3, 3]}],
     "animations": [{"type": "bounce", "height": 0.5, "speed": 0.5, "start": 0, "end": 8}]},
    {"id": "ball", "position": [4, 0, 3],
     "parts": [{"shape": "sphere", "color": "#1E88E5", "position": [0, 0.5, 0], "scale": [1, 1, 1]}],
     "animations": [{"type": "bounce", "height": 1.5, "speed": 1.5, "start": 0, "end": 8}]}
  ],
  "captions": [
    {"text": "Güneşli bir günde araba yolda ilerliyor", "start": 0.5, "end": 5},
    {"text": "Örnek sahne", "start": 5, "end": 8}
  ]
}
""".trimIndent()
}
