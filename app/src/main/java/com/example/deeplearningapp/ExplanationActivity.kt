package com.example.deeplearningapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_explanation.*
import kotlin.Array as Array

class ExplanationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explanation)
        val speciesName = this.intent.getStringExtra(EXTRA_MESSAGE)
        textView2.text = speciesName.toString()
        val arrayOfStrings = getStringArray(speciesName)
        textCommonName.text = arrayOfStrings[0]
        textClass.text = arrayOfStrings[1]
        textOrder.text = arrayOfStrings[2]
        textFamily.text = arrayOfStrings[3]
        textDescription.text = arrayOfStrings[4]

    }
    /*fun returnToMain (view : View){
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivityIfNeeded(intent, 0)
        finish()
    } */
    fun getStringID (name : String): Int {
        val stringName = name
        val stringID = getResources().getIdentifier(stringName, "string", packageName)
        return if (stringID == 0) {
            Log.d("Loading String", "String name is invalid.")
        } else {
            return stringID
        }

    }
    fun getStringArray (name : String): Array<String> {
        val stringName = name
        val arrayID = getResources().getIdentifier(stringName, "string-array", packageName)
        return resources.getStringArray(arrayID)

    }



}