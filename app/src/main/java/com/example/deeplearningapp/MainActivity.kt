package com.example.deeplearningapp

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.custom.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.text.SimpleDateFormat

const val EXTRA_MESSAGE = "com.example.deeplearningapp.MESSAGE"
class MainActivity : AppCompatActivity() {

    val CAMERA_PERMISSION = arrayOf(Manifest.permission.CAMERA)
    val STORAGE_PERMISSION = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE
        ,Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    val FLAG_PERM_CAMERA = 98
    val FLAG_PERM_STORAGE = 99

    val FLAG_REQ_CAMERA = 101
    val FLAG_REQ_STORAGE = 102
    val IMAGE_WIDTH = 380
    val IMAGE_HEIGHT = 380
    var speciesName : String = ""


    var inputimage = Array(1) { Array(IMAGE_WIDTH) { Array(IMAGE_HEIGHT) { FloatArray(3) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkPermission(STORAGE_PERMISSION, FLAG_PERM_STORAGE)) {
            setViews()
        }

    }

    fun setViews() {
        buttonCamera.setOnClickListener {
            openCameraAndPredict()
        }
        buttonGallery.setOnClickListener {
            openGalleryAndPredict()
        }

        val description : View = findViewById(R.id.buttonDescription)
        description.visibility = View.GONE



    }


    fun openCameraAndPredict() {
        if (checkPermission(CAMERA_PERMISSION, FLAG_PERM_CAMERA)) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, FLAG_REQ_CAMERA)
        }
    }

    fun openGalleryAndPredict() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = MediaStore.Images.Media.CONTENT_TYPE
        startActivityForResult(intent, FLAG_REQ_STORAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                FLAG_REQ_CAMERA -> {
                    if (data?.extras?.get("data") != null) {
                        val bitmap = data?.extras?.get("data") as Bitmap
                        imagePreview.setImageBitmap(bitmap)
                        val processedImage = preprocessImage(bitmap)

                        val uri = saveImageFile(newFileName(), "image/jpg", bitmap)
                        imagePreview.setImageURI(uri)
                        val input = processedImage
                        this.inputimage = input
                        predict(inputimage)
                    }
                }
                FLAG_REQ_STORAGE -> {
                    val uri = data?.data

                    imagePreview.setImageURI(uri)
                    if (Build.VERSION.SDK_INT < 28) {
                        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri!!)
                        val processedImage_fromuri = preprocessImage(bitmap)
                        val input =  processedImage_fromuri
                        this.inputimage = input
                        predict(inputimage)
                    } else {
                        val source = ImageDecoder.createSource(this.contentResolver, uri!!)
                        val map = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.RGBA_F16, true)
                        val processedImage = preprocessImage(map)
                        val input  = processedImage
                        this.inputimage = input
                        predict(inputimage)

                    }
                }
            }
        }
    }
    fun sendPredictionResults (view : View){
        val message : String = speciesName
        val intent = Intent(this, ExplanationActivity::class.java).apply {
            putExtra(EXTRA_MESSAGE, message)
        }
        /* startActivity(intent) */ // Disabled. Calling ExplanationActivity induces bug.
    }

    fun saveImageFile(filename: String, mimeType: String, bitmap: Bitmap): Uri? {
        var values = ContentValues()
        values.put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        values.put(MediaStore.Images.Media.MIME_TYPE, mimeType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        try {
            if (uri != null) {
                var descriptor = contentResolver.openFileDescriptor(uri, "w")
                if (descriptor != null) {
                    val fos = FileOutputStream(descriptor.fileDescriptor)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                    fos.close()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                    }
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e("File", "error=${e.localizedMessage}")
        }
        return uri
    }

    fun newFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
        val filename = sdf.format(System.currentTimeMillis())

        return "$filename.jpg"
    }

    fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val editedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_WIDTH, IMAGE_HEIGHT, true)
        val input = Array(1) { Array(IMAGE_WIDTH) { Array(IMAGE_HEIGHT) { FloatArray(3) } } }
        for (x in 0..379) {
            for (y in 0..379) {
                val pixel = editedBitmap.getPixel(x, y)
                input[0][x][y][0] = (Color.red(pixel)) / 255.0f
                input[0][x][y][1] = (Color.green(pixel)) / 255.0f
                input[0][x][y][2] = (Color.blue(pixel)) / 255.0f

            }
        }
        return input
    }

    
    fun checkPermission(permissions: Array<out String>, flag: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in permissions) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(this, permissions, flag)
                    return false
                }
            }
        }
        return true
    }

    fun predict(image: Array<Array<Array<FloatArray>>>) {
        val localModel = FirebaseCustomLocalModel.Builder()
            .setAssetFilePath("effnetb4.tflite")
            .build()
        val options = FirebaseModelInterpreterOptions.Builder(localModel).build()
        val interpreter = FirebaseModelInterpreter.getInstance(options)
        val inputOutputOptions = FirebaseModelInputOutputOptions.Builder()
            .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 380, 380, 3))
            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 59))
            .build()
        val inputs = FirebaseModelInputs.Builder()
            .add(image) // add() as many input arrays as your model requires
            .build()
        val startTimeForReference = SystemClock.uptimeMillis()
        interpreter!!.run(inputs, inputOutputOptions)
            .addOnSuccessListener { result ->
                val endTimeForReference = SystemClock.uptimeMillis()
                val output = result.getOutput<Array<FloatArray>>(0)
                val probabilities = output[0]
                val reader = BufferedReader(
                    InputStreamReader(assets.open("newlabels.txt"))
                )
                val labelList = arrayOfNulls<String>(59)



                for (i in probabilities.indices) {

                    val label = reader.readLine()
                    labelList[i] = label
                    Log.i("MLKit", String.format("%s: %1.4f", label, probabilities[i]))
                }
                val maxIndex = probabilities.indexOf(probabilities.max()!!)
                val label = labelList[maxIndex]
                speciesName = label.toString()
                val textOutput : String = String.format("%s : %1.4f", label, probabilities.max())
                textViewName.text = textOutput
                var high1 : Float = Float.MIN_VALUE
                var high2 : Float = Float.MIN_VALUE
                var secondIndex : Int = Int.MIN_VALUE
                for ((index, value) in probabilities.withIndex()) {
                    if (value > high1) {
                        high2 = high1
                        high1 = value
                    }
                    else if (value > high2) {
                        high2 = value
                        secondIndex = index
                    }

                }

                //val secondMaxIndex = probabilities.indexOf(probabilities.max()!!)
                val secondLabel = labelList[secondIndex]
                val textSecondOutput : String = String.format("%s : %1.4f", secondLabel, high2)
                textViewSecond.text = textSecondOutput
                val description : View = findViewById(R.id.buttonDescription)
                description.visibility = View.VISIBLE
                val timeElapsed : Float = (endTimeForReference - startTimeForReference) / 1000f
                textInference.text = ("Inference Time = " + timeElapsed + "second(s)")






            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                // ...
                Log.d("MLKit", "Failed")
            }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int
        , permissions: Array<out String>
        , grantResults: IntArray
    ) {
        when (requestCode) {
            FLAG_PERM_STORAGE -> {
                for (grant in grantResults) {
                    if (grant != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Please grant storage permissions.", Toast.LENGTH_LONG)
                            .show()

                        return
                    }
                }
                setViews()
            }
            FLAG_PERM_CAMERA -> {
                for (grant in grantResults) {
                    if (grant != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Please grant camera permissions.", Toast.LENGTH_LONG)
                            .show()
                        return
                    }
                }
                openCameraAndPredict()
            }
        }


    }
}




