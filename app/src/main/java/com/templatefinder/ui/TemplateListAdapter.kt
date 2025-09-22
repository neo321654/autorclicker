package com.templatefinder.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.templatefinder.R
import com.templatefinder.model.Template

class TemplateListAdapter(context: Context, private val templates: List<Pair<String, Template>>) :
    ArrayAdapter<Pair<String, Template>>(context, 0, templates) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.dialog_template_item, parent, false)
        }

        val currentItem = templates[position]

        val previewImage = view!!.findViewById<ImageView>(R.id.template_preview_image)
        val nameText = view.findViewById<TextView>(R.id.template_name_text)

        previewImage.setImageBitmap(currentItem.second.templateBitmap)
        nameText.text = currentItem.first

        return view
    }
}