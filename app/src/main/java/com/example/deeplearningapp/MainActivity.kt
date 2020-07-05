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
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media.getBitmap
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.get
import com.google.firebase.ml.custom.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.text.SimpleDateFormat

class MainActivity : AppCompatActivity() {

    val CAMERA_PERMISSION = arrayOf(Manifest.permission.CAMERA)
    val STORAGE_PERMISSION = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE
        , Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    val FLAG_PERM_CAMERA = 98
    val FLAG_PERM_STORAGE = 99

    val FLAG_REQ_CAMERA = 101
    val FLAG_REQ_STORAGE = 102
    val IMAGE_WIDTH = 380
    val IMAGE_HEIGHT = 380


    var inputimage = Array(1) { Array(300) { Array(300) { FloatArray(3) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkPermission(STORAGE_PERMISSION, FLAG_PERM_STORAGE)) {
            setViews()
        }
        //val intent = Intent(this, SubActivity::class.java)
        //intent.putExtra("inputimage", this.inputimage)
        //buttonPredict.setOnClickListener{startActivity(intent)}

    }

    fun setViews() {
        buttonCamera.setOnClickListener {
            openCamera()
        }
        buttonGallery.setOnClickListener {
            openGallery()
        }
        buttonPredict.setOnClickListener{
            if (inputimage != null){
                predict(inputimage)
            }
        }



    }


    fun openCamera() {
        if (checkPermission(CAMERA_PERMISSION, FLAG_PERM_CAMERA)) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, FLAG_REQ_CAMERA)
        }
    }

    fun openGallery() {
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
                    } else {
                        val source = ImageDecoder.createSource(this.contentResolver, uri!!)
                        val map = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.RGBA_F16, true)
                        val processedImage = preprocessImage(map)
                        val input  = processedImage
                        this.inputimage = input

                    }

                }
            }
        }
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
        val editedBitmap = Bitmap.createScaledBitmap(bitmap, 300, 300, true)
        val input = Array(1) { Array(300) { Array(300) { FloatArray(3) } } }
        for (x in 0..299) {
            for (y in 0..299) {
                val pixel = editedBitmap.getPixel(x, y)
                input[0][x][y][0] = (Color.red(pixel)) / 255.0f
                input[0][x][y][1] = (Color.green(pixel)) / 255.0f
                input[0][x][y][2] = (Color.blue(pixel)) / 255.0f

            }
        }
        return input
    }

    /*
    * 여기서 부터 권한처리 관련 함수
    */
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
            .setAssetFilePath("effnet3.tflite")
            .build()
        val options = FirebaseModelInterpreterOptions.Builder(localModel).build()
        val interpreter = FirebaseModelInterpreter.getInstance(options)
        val inputOutputOptions = FirebaseModelInputOutputOptions.Builder()
            .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 300, 300, 3))
            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 40))
            .build()
        val inputs = FirebaseModelInputs.Builder()
            .add(image) // add() as many input arrays as your model requires
            .build()
        interpreter!!.run(inputs, inputOutputOptions)
            .addOnSuccessListener { result ->
                val output = result.getOutput<Array<FloatArray>>(0)
                val probabilities = output[0]
                val reader = BufferedReader(
                    InputStreamReader(assets.open("labels.txt"))
                )
                val labellist = arrayOfNulls<String>(40)



                for (i in probabilities.indices) {

                    val label = reader.readLine()
                    labellist[i] = label
                    Log.i("MLKit", String.format("%s: %1.4f", label, probabilities[i]))
                }
                val maxIndex = probabilities.indexOf(probabilities.max()!!)
                val label = labellist[maxIndex]
                val textOutput : String = String.format("%s : %1.4f", label, probabilities.max())
                textViewName.text = textOutput




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
                        Toast.makeText(this, "PLease grant camera permissions.", Toast.LENGTH_LONG)
                            .show()
                        return
                    }
                }
                openCamera()
            }
        }


    }
}


