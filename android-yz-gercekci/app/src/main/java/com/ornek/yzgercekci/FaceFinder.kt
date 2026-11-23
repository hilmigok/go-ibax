package com.ornek.yzgercekci

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.TimeUnit

/**
 * Görüntüdeki yüzleri ve göz/ağız noktalarını bulur (Google ML Kit, cihaz içinde,
 * ücretsiz ve internetsiz). Arka plan iş parçacığında çağrılmalıdır.
 */
object FaceFinder {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                // Geniş çekimlerdeki küçük yüzlere mimik uygulamanın anlamı yok.
                .setMinFaceSize(0.06f)
                .build()
        )
    }

    fun find(bmp: Bitmap): List<FaceRig> {
        val faces = try {
            Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)), 20, TimeUnit.SECONDS)
        } catch (e: Exception) {
            return emptyList()
        }
        val out = ArrayList<FaceRig>()
        for ((i, f) in faces.withIndex()) {
            val le = f.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: continue
            val re = f.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: continue
            // Gözler arası en az 14 piksel değilse yüz çok küçük.
            if (kotlin.math.hypot(re.x - le.x, re.y - le.y) < 14f) continue
            val ml = f.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
            val mr = f.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
            val mb = f.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
            val seed = i * 7919 + f.boundingBox.left
            out += if (ml != null && mr != null && mb != null) {
                FaceRig(le.x, le.y, re.x, re.y, ml.x, ml.y, mr.x, mr.y, mb.x, mb.y, seed)
            } else {
                FaceRig.fromEyes(le.x, le.y, re.x, re.y, seed)
            }
            if (out.size >= 6) break
        }
        return out
    }
}
