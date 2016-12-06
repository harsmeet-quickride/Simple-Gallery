package com.simplemobiletools.gallery.adapters

import android.os.Build
import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView
import android.view.*
import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback
import com.bignerdranch.android.multiselector.MultiSelector
import com.bignerdranch.android.multiselector.SwappingHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.StringSignature
import com.simplemobiletools.filepicker.asynctasks.CopyMoveTask
import com.simplemobiletools.filepicker.dialogs.ConfirmationDialog
import com.simplemobiletools.filepicker.extensions.toast
import com.simplemobiletools.fileproperties.dialogs.PropertiesDialog
import com.simplemobiletools.gallery.R
import com.simplemobiletools.gallery.activities.SimpleActivity
import com.simplemobiletools.gallery.dialogs.CopyDialog
import com.simplemobiletools.gallery.dialogs.RenameFileDialog
import com.simplemobiletools.gallery.extensions.beVisibleIf
import com.simplemobiletools.gallery.extensions.openEditor
import com.simplemobiletools.gallery.extensions.shareMedia
import com.simplemobiletools.gallery.extensions.shareMedium
import com.simplemobiletools.gallery.helpers.Config
import com.simplemobiletools.gallery.models.Medium
import kotlinx.android.synthetic.main.photo_video_item.view.*
import kotlinx.android.synthetic.main.photo_video_tmb.view.*
import java.io.File
import java.util.*

class MediaAdapter(val activity: SimpleActivity, var media: MutableList<Medium>, val listener: MediaOperationsListener?, val itemClick: (Medium) -> Unit) :
        RecyclerView.Adapter<MediaAdapter.ViewHolder>() {
    val multiSelector = MultiSelector()
    val views = ArrayList<View>()
    val config = Config.newInstance(activity)

    companion object {
        var actMode: ActionMode? = null
        var displayFilenames = false
        val markedItems = HashSet<Int>()

        fun toggleItemSelection(itemView: View, select: Boolean, pos: Int = -1) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                itemView.medium_thumbnail_holder.isSelected = select
            else
                itemView.medium_thumbnail.isSelected = select

            if (pos == -1)
                return

            if (select)
                markedItems.add(pos)
            else
                markedItems.remove(pos)
        }
    }

    val multiSelectorMode = object : ModalMultiSelectorCallback(multiSelector) {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.cab_properties -> {
                    showProperties()
                    true
                }
                R.id.cab_rename -> {
                    renameFile()
                    true
                }
                R.id.cab_edit -> {
                    editFile()
                    true
                }
                R.id.cab_share -> {
                    shareMedia()
                    true
                }
                R.id.cab_copy_move -> {
                    displayCopyDialog()
                    true
                }
                R.id.cab_delete -> {
                    askConfirmDelete()
                    true
                }
                else -> false
            }
        }

        override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
            super.onCreateActionMode(actionMode, menu)
            actMode = actionMode
            activity.menuInflater.inflate(R.menu.cab_media, menu)
            return true
        }

        override fun onPrepareActionMode(actionMode: ActionMode?, menu: Menu): Boolean {
            menu.findItem(R.id.cab_rename).isVisible = multiSelector.selectedPositions.size <= 1
            menu.findItem(R.id.cab_edit).isVisible = multiSelector.selectedPositions.size <= 1

            return true
        }

        override fun onDestroyActionMode(actionMode: ActionMode?) {
            super.onDestroyActionMode(actionMode)
            views.forEach { toggleItemSelection(it, false) }
            markedItems.clear()
        }
    }

    private fun showProperties() {
        val selections = multiSelector.selectedPositions
        if (selections.size <= 1) {
            PropertiesDialog(activity, media[selections[0]].path, config.showHiddenFolders)
        } else {
            val paths = ArrayList<String>()
            selections.forEach { paths.add(media[it].path) }
            PropertiesDialog(activity, paths, config.showHiddenFolders)
        }
    }

    private fun renameFile() {
        RenameFileDialog(activity, getCurrentFile()) {
            listener?.refreshItems()
            activity.runOnUiThread {
                actMode?.finish()
            }
        }
    }

    private fun editFile() {
        activity.openEditor(getCurrentFile())
        actMode?.finish()
    }

    private fun shareMedia() {
        val selections = multiSelector.selectedPositions
        if (selections.size <= 1) {
            activity.shareMedium(getSelectedMedia()[0])
        } else {
            activity.shareMedia(getSelectedMedia())
        }
    }

    private fun displayCopyDialog() {
        val files = ArrayList<File>()
        val positions = multiSelector.selectedPositions
        positions.forEach { files.add(File(media[it].path)) }

        CopyDialog(activity, files, object : CopyMoveTask.CopyMoveListener {
            override fun copySucceeded(deleted: Boolean, copiedAll: Boolean) {
                if (deleted) {
                    activity.toast(if (copiedAll) R.string.moving_success else R.string.moving_success_partial)
                } else {
                    activity.toast(if (copiedAll) R.string.copying_success else R.string.copying_success_partial)
                }
                listener?.refreshItems()
                actMode?.finish()
            }

            override fun copyFailed() {
                activity.toast(R.string.copy_move_failed)
            }
        })
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            actMode?.finish()
            prepareForDeleting()
        }
    }

    private fun getCurrentFile() = File(media[multiSelector.selectedPositions[0]].path)

    private fun prepareForDeleting() {
        val selections = multiSelector.selectedPositions
        val paths = ArrayList<String>(selections.size)
        selections.forEach { paths.add(media[it].path) }
        listener?.prepareForDeleting(paths)
    }

    private fun getSelectedMedia(): List<Medium> {
        val positions = multiSelector.selectedPositions
        val selectedMedia = ArrayList<Medium>(positions.size)
        positions.forEach { selectedMedia.add(media[it]) }
        return selectedMedia
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.photo_video_item, parent, false)
        return ViewHolder(view, itemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        views.add(holder.bindView(activity, multiSelectorMode, multiSelector, media[position], position))
    }

    override fun getItemCount() = media.size

    fun updateMedia(newMedia: ArrayList<Medium>) {
        media = newMedia
        notifyDataSetChanged()
    }

    fun updateDisplayFilenames(display: Boolean) {
        displayFilenames = display
        notifyDataSetChanged()
    }

    class ViewHolder(view: View, val itemClick: (Medium) -> (Unit)) : SwappingHolder(view, MultiSelector()) {
        fun bindView(activity: SimpleActivity, multiSelectorCallback: ModalMultiSelectorCallback, multiSelector: MultiSelector, medium: Medium, pos: Int): View {
            itemView.play_outline.visibility = if (medium.isVideo) View.VISIBLE else View.GONE
            itemView.file_name.beVisibleIf(displayFilenames)
            itemView.file_name.text = medium.name
            toggleItemSelection(itemView, markedItems.contains(pos), pos)

            val path = medium.path
            val timestampSignature = StringSignature(medium.date_modified.toString())
            if (medium.isGif()) {
                Glide.with(activity).load(path).asGif().diskCacheStrategy(DiskCacheStrategy.NONE).signature(timestampSignature).into(itemView.medium_thumbnail)
            } else if (medium.isPng()) {
                Glide.with(activity).load(path).asBitmap().format(DecodeFormat.PREFER_ARGB_8888).diskCacheStrategy(DiskCacheStrategy.RESULT)
                        .signature(timestampSignature).placeholder(R.color.tmb_background).centerCrop().into(itemView.medium_thumbnail)
            } else {
                Glide.with(activity).load(path).diskCacheStrategy(DiskCacheStrategy.RESULT).signature(timestampSignature)
                        .placeholder(R.color.tmb_background).centerCrop().crossFade().into(itemView.medium_thumbnail)
            }

            itemView.setOnClickListener { viewClicked(multiSelector, medium, pos) }
            itemView.setOnLongClickListener {
                if (!multiSelector.isSelectable) {
                    activity.startSupportActionMode(multiSelectorCallback)
                    multiSelector.setSelected(this, true)
                    actMode?.title = multiSelector.selectedPositions.size.toString()
                    toggleItemSelection(itemView, true, pos)
                    actMode?.invalidate()
                }
                true
            }

            return itemView
        }

        fun viewClicked(multiSelector: MultiSelector, medium: Medium, pos: Int) {
            if (multiSelector.isSelectable) {
                val isSelected = multiSelector.selectedPositions.contains(layoutPosition)
                multiSelector.setSelected(this, !isSelected)
                toggleItemSelection(itemView, !isSelected, pos)

                val selectedCnt = multiSelector.selectedPositions.size
                if (selectedCnt == 0) {
                    actMode?.finish()
                } else {
                    actMode?.title = selectedCnt.toString()
                }
                actMode?.invalidate()
            } else {
                itemClick(medium)
            }
        }
    }

    interface MediaOperationsListener {
        fun refreshItems()

        fun prepareForDeleting(paths: ArrayList<String>)
    }
}
