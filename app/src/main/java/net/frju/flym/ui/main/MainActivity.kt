/*
 * Copyright (c) 2012-2018 Frederic Julian
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package net.frju.flym.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.arch.lifecycle.Observer
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.support.v4.app.FragmentTransaction
import android.support.v4.view.GravityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PopupMenu
import androidx.core.view.isGone
import androidx.core.widget.toast
import com.rometools.opml.feed.opml.Attribute
import com.rometools.opml.feed.opml.Opml
import com.rometools.opml.feed.opml.Outline
import com.rometools.opml.io.impl.OPML20Generator
import com.rometools.rome.io.WireFeedInput
import com.rometools.rome.io.WireFeedOutput
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_edit_feed.view.*
import kotlinx.android.synthetic.main.fragment_entries.*
import kotlinx.android.synthetic.main.view_main_containers.*
import kotlinx.android.synthetic.main.view_main_drawer_header.*
import net.fred.feedex.R
import net.frju.flym.App
import net.frju.flym.data.entities.Feed
import net.frju.flym.data.entities.FeedWithCount
import net.frju.flym.data.utils.PrefConstants
import net.frju.flym.service.AutoRefreshJobService
import net.frju.flym.ui.about.AboutActivity
import net.frju.flym.ui.entries.EntriesFragment
import net.frju.flym.ui.entrydetails.EntryDetailsActivity
import net.frju.flym.ui.entrydetails.EntryDetailsFragment
import net.frju.flym.ui.feeds.FeedAdapter
import net.frju.flym.ui.feeds.FeedGroup
import net.frju.flym.ui.feeds.FeedListEditActivity
import net.frju.flym.ui.settings.SettingsActivity
import net.frju.flym.utils.closeKeyboard
import net.frju.flym.utils.getPrefBoolean
import net.frju.flym.utils.putPrefBoolean
import net.frju.flym.utils.setupNoActionBarTheme
import org.jetbrains.anko.*
import org.jetbrains.anko.sdk21.listeners.onClick
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.io.*
import java.net.URL
import java.util.*


class MainActivity : AppCompatActivity(), MainNavigator, AnkoLogger {

    companion object {
        const val EXTRA_FROM_NOTIF = "EXTRA_FROM_NOTIF"

        var isInForeground = false

        private const val TAG_DETAILS = "TAG_DETAILS"
        private const val TAG_MASTER = "TAG_MASTER"

        private const val OLD_GNEWS_TO_IGNORE = "http://news.google.com/news?"

        private const val AUTO_IMPORT_OPML_REQUEST_CODE = 1
        private const val WRITE_OPML_REQUEST_CODE = 2
        private const val READ_OPML_REQUEST_CODE = 3
        private val NEEDED_PERMS = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        private val BACKUP_OPML = File(Environment.getExternalStorageDirectory(), "/Flym_auto_backup.opml")
        private const val RETRIEVE_FULLTEXT_OPML_ATTR = "retrieveFullText"
    }

    private val feedGroups = mutableListOf<FeedGroup>()
    private val feedAdapter = FeedAdapter(feedGroups)

    override fun onCreate(savedInstanceState: Bundle?) {
        setupNoActionBarTheme()

        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_main)

        more.onClick {
            it?.let { view ->
                PopupMenu(this@MainActivity, view).apply {
                    menuInflater.inflate(R.menu.menu_drawer_header, menu)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.reorder -> startActivity<FeedListEditActivity>()
                            R.id.import_feeds -> pickOpml()
                            R.id.export_feeds -> exportOpml()
                            R.id.menu_entries__about -> goToAboutMe()
                            R.id.menu_entries__settings -> goToSettings()
                        }
                        true
                    }
                    show()
                }
            }
        }
        nav.layoutManager = LinearLayoutManager(this)
        nav.adapter = feedAdapter
//      by utkarsh
//        val feedToAdd = Feed(link = "http://cryptodaily.co.uk/feed", title = "Crypto Daily")
//        feedToAdd.retrieveFullText = true // do that automatically for google news feeds
//          to add feed directly
//        context.doAsync { App.db.feedDao().insert(feedToAdd) }

        //to delete feed by url
//        context.doAsync { App.db.feedDao().delete(App.db.feedDao().findByLink(feedToAdd.link)!!) }

        add_feed_fab.onClick {
            FeedSearchDialog(this).show()
        }

        App.db.feedDao().observeAllWithCount.observe(this@MainActivity, Observer { nullableFeeds ->
            nullableFeeds?.let { feeds ->
                val newFeedGroups = mutableListOf<FeedGroup>()

                val all = FeedWithCount(feed = Feed().apply {
                    id = Feed.ALL_ENTRIES_ID
                    title = getString(R.string.all_entries)
                }, entryCount = feeds.sumBy { it.entryCount })
                newFeedGroups.add(FeedGroup(all, listOf()))

                val subFeedMap = feeds.groupBy { it.feed.groupId }

                newFeedGroups.addAll(
                        subFeedMap[null]?.map { FeedGroup(it, subFeedMap[it.feed.id].orEmpty()) }.orEmpty()
                )

                // Do not always call notifyParentDataSetChanged to avoid selection loss during refresh
                if (hasFeedGroupsChanged(feedGroups, newFeedGroups)) {
                    feedGroups.clear()
                    feedGroups += newFeedGroups
                    feedAdapter.notifyParentDataSetChanged(true)

                    if (hasFetchingError()) {
                        drawer_hint.textColor = Color.RED
                        drawer_hint.textResource = R.string.drawer_fetch_error_explanation
                    } else {
                        drawer_hint.textColor = Color.WHITE
                        drawer_hint.textResource = R.string.drawer_explanation
                    }
                }

                feedAdapter.onFeedClick { view, feedWithCount ->
                    goToEntriesList(feedWithCount.feed)
                    closeDrawer()
                }

                feedAdapter.onFeedLongClick { view, feedWithCount ->
                    PopupMenu(this, view).apply {
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.mark_all_as_read -> doAsync {
                                    when {
                                        feedWithCount.feed.id == Feed.ALL_ENTRIES_ID -> App.db.entryDao().markAllAsRead()
                                        feedWithCount.feed.isGroup -> App.db.entryDao().markGroupAsRead(feedWithCount.feed.id)
                                        else -> App.db.entryDao().markAsRead(feedWithCount.feed.id)
                                    }
                                }
                                R.id.edit_feed -> {
                                    @SuppressLint("InflateParams")
                                    val input = layoutInflater.inflate(R.layout.dialog_edit_feed, null, false).apply {
                                        feed_name.setText(feedWithCount.feed.title)
                                        if (feedWithCount.feed.isGroup) {
                                            feed_link.isGone = true
                                        } else {
                                            feed_link.setText(feedWithCount.feed.link)
                                        }
                                    }

                                    AlertDialog.Builder(this@MainActivity)
                                            .setTitle(R.string.menu_edit_feed)
                                            .setView(input)
                                            .setPositiveButton(android.R.string.ok) { dialog, which ->
                                                val newName = input.feed_name.text.toString()
                                                val newLink = input.feed_link.text.toString()
                                                if (newName.isNotBlank() && (newLink.isNotBlank() || feedWithCount.feed.isGroup)) {
                                                    doAsync {
                                                        // Need to do a copy to not directly modify the memory and being able to detect changes
                                                        val newFeed = feedWithCount.feed.copy().apply {
                                                            title = newName
                                                            if (!feedWithCount.feed.isGroup) {
                                                                link = newLink
                                                            }
                                                        }
                                                        App.db.feedDao().update(newFeed)
                                                    }
                                                }
                                            }
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show()
                                }
                                R.id.reorder -> startActivity<FeedListEditActivity>()
                                R.id.delete -> {
                                    AlertDialog.Builder(this@MainActivity)
                                            .setTitle(feedWithCount.feed.title)
                                            .setMessage(if (feedWithCount.feed.isGroup) R.string.question_delete_group else R.string.question_delete_feed)
                                            .setPositiveButton(android.R.string.yes) { _, _ ->
                                                doAsync { App.db.feedDao().delete(feedWithCount.feed) }
                                            }.setNegativeButton(android.R.string.no, null)
                                            .show()
                                }
                                R.id.enable_full_text_retrieval -> doAsync { App.db.feedDao().enableFullTextRetrieval(feedWithCount.feed.id) }
                                R.id.disable_full_text_retrieval -> doAsync { App.db.feedDao().disableFullTextRetrieval(feedWithCount.feed.id) }
                            }
                            true
                        }
                        inflate(R.menu.menu_drawer_feed)

                        when {
                            feedWithCount.feed.id == Feed.ALL_ENTRIES_ID -> {
                                menu.findItem(R.id.edit_feed).isVisible = false
                                menu.findItem(R.id.delete).isVisible = false
                                menu.findItem(R.id.reorder).isVisible = false
                                menu.findItem(R.id.enable_full_text_retrieval).isVisible = false
                                menu.findItem(R.id.disable_full_text_retrieval).isVisible = false
                            }
                            feedWithCount.feed.isGroup -> {
                                menu.findItem(R.id.enable_full_text_retrieval).isVisible = false
                                menu.findItem(R.id.disable_full_text_retrieval).isVisible = false
                            }
                            feedWithCount.feed.retrieveFullText -> menu.findItem(R.id.enable_full_text_retrieval).isVisible = false
                            else -> menu.findItem(R.id.disable_full_text_retrieval).isVisible = false
                        }

                        show()
                    }
                }
            }
        })

        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_menu_24dp)
        toolbar.setNavigationOnClickListener { toggleDrawer() }

        if (savedInstanceState == null) {
            // First open => we open the drawer for you
            if (getPrefBoolean(PrefConstants.FIRST_OPEN, true)) {
                putPrefBoolean(PrefConstants.FIRST_OPEN, false)
                openDrawer()

                if (isOldFlymAppInstalled()) {
                    AlertDialog.Builder(this)
                            .setTitle(R.string.welcome_title_with_opml_import)
                            .setPositiveButton(android.R.string.yes) { _, _ ->
                                autoImportOpml()
                            }
                            .setNegativeButton(android.R.string.no, null)
                            .show()
                } else {
                    AlertDialog.Builder(this)
                            .setTitle(R.string.welcome_title)
                            .setPositiveButton(android.R.string.yes) { _, _ ->
                                FeedSearchDialog(this).show()
                            }
                            .setNegativeButton(android.R.string.no, null)
                            .show()
                }
            } else {
                closeDrawer()
            }

            goToEntriesList(null)
        }

        AutoRefreshJobService.initAutoRefresh(this)

        handleImplicitIntent(intent)

    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        handleImplicitIntent(intent)
    }

    private fun handleImplicitIntent(intent: Intent?) {
        // Has to be called on onStart (when the app is closed) and on onNewIntent (when the app is in the background)

        //Add feed urls from Open with
        if (intent?.action.equals(Intent.ACTION_VIEW)) {
            val search: String = intent?.data.toString()
            FeedSearchDialog(this, search).show()
            setIntent(null)
        }
        // Add feed urls from Share menu
        if (intent?.action.equals(Intent.ACTION_SEND)) {
            if (intent?.hasExtra(Intent.EXTRA_TEXT) == true) {
                val search = intent.getStringExtra(Intent.EXTRA_TEXT)
                FeedSearchDialog(this, search).show()
            }
            setIntent(null)
        }

        // If we just clicked on the notification, let's go back to the default view
        if (intent?.getBooleanExtra(EXTRA_FROM_NOTIF, false) == true && feedGroups.isNotEmpty()) {
            feedAdapter.selectedItemId = Feed.ALL_ENTRIES_ID
            goToEntriesList(feedGroups[0].feedWithCount.feed)
            bottom_navigation.selectedItemId = R.id.unreads
        }
    }

    override fun onResume() {
        super.onResume()

        isInForeground = true
        notificationManager.cancel(0)
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        feedAdapter.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        feedAdapter.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onBackPressed() {
        if (drawer?.isDrawerOpen(GravityCompat.START) == true) {
            drawer?.closeDrawer(GravityCompat.START)
        } else if (toolbar.hasExpandedActionView()) {
            toolbar.collapseActionView()
        } else if (!goBack()) {
            super.onBackPressed()
        }
    }

    override fun goToEntriesList(feed: Feed?) {
        clearDetails()
        containers_layout.state = MainNavigator.State.TWO_COLUMNS_EMPTY

        // We try to reuse the fragment to avoid loosing the bottom tab position
        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame_master)
        if (currentFragment is EntriesFragment) {
            currentFragment.feed = feed
        } else {
            val master = EntriesFragment.newInstance(feed)
            supportFragmentManager
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.frame_master, master, TAG_MASTER)
                    .commitAllowingStateLoss()
        }
    }

    override fun goToEntryDetails(entryId: String, allEntryIds: List<String>) {
        closeKeyboard()

        if (containers_layout.hasTwoColumns()) {
            containers_layout.state = MainNavigator.State.TWO_COLUMNS_WITH_DETAILS
            val fragment = EntryDetailsFragment.newInstance(entryId, allEntryIds)
            supportFragmentManager
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.frame_details, fragment, TAG_DETAILS)
                    .commitAllowingStateLoss()

            val listFragment = supportFragmentManager.findFragmentById(R.id.frame_master) as EntriesFragment
            listFragment.setSelectedEntryId(entryId)
        } else {
            if (getPrefBoolean(PrefConstants.OPEN_BROWSER_DIRECTLY, false)) {
                openInBrowser(entryId)
            } else {
                startActivity<EntryDetailsActivity>(EntryDetailsFragment.ARG_ENTRY_ID to entryId, EntryDetailsFragment.ARG_ALL_ENTRIES_IDS to allEntryIds.take(500)) // take() to avoid TransactionTooLargeException
            }
        }
    }

    override fun setSelectedEntryId(selectedEntryId: String) {
        val listFragment = supportFragmentManager.findFragmentById(R.id.frame_master) as EntriesFragment
        listFragment.setSelectedEntryId(selectedEntryId)
    }

    override fun goToAboutMe() {
        startActivity<AboutActivity>()
    }

    override fun goToSettings() {
        startActivity<SettingsActivity>()
    }

    private fun openInBrowser(entryId: String) {
        doAsync {
            App.db.entryDao().findByIdWithFeed(entryId)?.entry?.link?.let { url ->
                App.db.entryDao().markAsRead(listOf(entryId))
                browse(url)
            }
        }
    }

    private fun isOldFlymAppInstalled() =
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA).any { it.packageName == "net.fred.feedex" }

    private fun hasFeedGroupsChanged(feedGroups: List<FeedGroup>, newFeedGroups: List<FeedGroup>): Boolean {
        if (feedGroups != newFeedGroups) {
            return true
        }

        // Also need to check all sub groups (can't be checked in FeedGroup's equals)
        feedGroups.forEachIndexed { index, feedGroup ->
            if (feedGroup.feedWithCount != newFeedGroups[index].feedWithCount || feedGroup.subFeeds != newFeedGroups[index].subFeeds) {
                return true
            }
        }

        return false
    }

    private fun hasFetchingError(): Boolean {
        // Also need to check all sub groups (can't be checked in FeedGroup's equals)
        feedGroups.forEach { feedGroup ->
            if (feedGroup.feedWithCount.feed.fetchError || feedGroup.subFeeds.any { it.feed.fetchError }) {
                return true
            }
        }

        return false
    }

    private fun pickOpml() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*" // https://github.com/FredJul/Flym/issues/407
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, READ_OPML_REQUEST_CODE)
    }

    private fun exportOpml() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, "Flym_" + System.currentTimeMillis() + ".opml")
        }
        startActivityForResult(intent, WRITE_OPML_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == READ_OPML_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            resultData?.data?.also { uri -> importOpml(uri) }
        } else if (requestCode == WRITE_OPML_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            resultData?.data?.also { uri -> exportOpml(uri) }
        }
    }

    @AfterPermissionGranted(AUTO_IMPORT_OPML_REQUEST_CODE)
    private fun autoImportOpml() {
        if (!EasyPermissions.hasPermissions(this, *NEEDED_PERMS)) {
            EasyPermissions.requestPermissions(this, getString(R.string.welcome_title_with_opml_import), AUTO_IMPORT_OPML_REQUEST_CODE, *NEEDED_PERMS)
        } else {
            if (BACKUP_OPML.exists()) {
                importOpml(Uri.fromFile(BACKUP_OPML))
            } else {
                toast(R.string.cannot_find_feeds)
            }
        }
    }

    private fun importOpml(uri: Uri) {
        doAsync {
            try {
                InputStreamReader(contentResolver.openInputStream(uri)).use { reader -> parseOpml(reader) }
            } catch (e: Exception) {
                try {
                    // We try to remove the opml version number, it may work better in some cases
                    val content = BufferedInputStream(contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                    val fixedReader = StringReader(content.replace("<opml version=['\"][0-9]\\.[0-9]['\"]>".toRegex(), "<opml>"))
                    parseOpml(fixedReader)
                } catch (e: Exception) {
                    uiThread { toast(R.string.cannot_find_feeds) }
                }
            }
        }
    }

    private fun exportOpml(uri: Uri) {
        doAsync {
            try {
                OutputStreamWriter(contentResolver.openOutputStream(uri), Charsets.UTF_8).use { writer -> exportOpml(writer) }
                contentResolver.query(uri, null, null, null, null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                        uiThread { toast(String.format(getString(R.string.message_exported_to), fileName)) }
                    }
                }
            } catch (e: Exception) {
                uiThread { toast(R.string.error_feed_export) }
            }
        }
    }

    private fun parseOpml(opmlReader: Reader) {
        var genId = 1L
        val feedList = mutableListOf<Feed>()
        val opml = WireFeedInput().build(opmlReader) as Opml
        opml.outlines.forEach { outline ->
            if (outline.xmlUrl != null || outline.children.isNotEmpty()) {
                val topLevelFeed = Feed().apply {
                    id = genId++
                    title = outline.title
                }

                if (outline.xmlUrl != null) {
                    if (!outline.xmlUrl.startsWith(OLD_GNEWS_TO_IGNORE)) {
                        topLevelFeed.link = outline.xmlUrl
                        topLevelFeed.retrieveFullText = outline.getAttributeValue(RETRIEVE_FULLTEXT_OPML_ATTR) == "true"
                        feedList.add(topLevelFeed)
                    }
                } else {
                    topLevelFeed.isGroup = true
                    feedList.add(topLevelFeed)

                    outline.children.filter { it.xmlUrl != null && !it.xmlUrl.startsWith(OLD_GNEWS_TO_IGNORE) }.forEach {
                        val subLevelFeed = Feed().apply {
                            id = genId++
                            title = it.title
                            link = it.xmlUrl
                            retrieveFullText = it.getAttributeValue(RETRIEVE_FULLTEXT_OPML_ATTR) == "true"
                            groupId = topLevelFeed.id
                        }

                        feedList.add(subLevelFeed)
                    }
                }
            }
        }

        if (feedList.isNotEmpty()) {
            App.db.feedDao().insert(*feedList.toTypedArray())
        }
    }

    private fun exportOpml(opmlWriter: Writer) {
        val feeds = App.db.feedDao().all.groupBy { it.groupId }

        val opml = Opml().apply {
            feedType = OPML20Generator().type
            encoding = "utf-8"
            created = Date()
            outlines = feeds[null]?.map { feed ->
                Outline(feed.title, if (feed.link.isNotBlank()) URL(feed.link) else null, null).apply {
                    children = feeds[feed.id]?.map {
                        Outline(it.title, if (it.link.isNotBlank()) URL(it.link) else null, null).apply {
                            if (it.retrieveFullText) {
                                attributes.add(Attribute(RETRIEVE_FULLTEXT_OPML_ATTR, "true"))
                            }
                        }
                    }
                    if (feed.retrieveFullText) {
                        attributes.add(Attribute(RETRIEVE_FULLTEXT_OPML_ATTR, "true"))
                    }
                }
            }
        }

        WireFeedOutput().output(opml, opmlWriter)
    }

    private fun closeDrawer() {
        if (drawer?.isDrawerOpen(GravityCompat.START) == true) {
            drawer?.postDelayed({ drawer.closeDrawer(GravityCompat.START) }, 100)
        }
    }

    private fun openDrawer() {
        if (drawer?.isDrawerOpen(GravityCompat.START) == false) {
            drawer?.openDrawer(GravityCompat.START)
        }
    }

    private fun toggleDrawer() {
        if (drawer?.isDrawerOpen(GravityCompat.START) == true) {
            drawer?.closeDrawer(GravityCompat.START)
        } else {
            drawer?.openDrawer(GravityCompat.START)
        }
    }

    private fun goBack(): Boolean {
        if (containers_layout.state == MainNavigator.State.TWO_COLUMNS_WITH_DETAILS && !containers_layout.hasTwoColumns()) {
            if (clearDetails()) {
                containers_layout.state = MainNavigator.State.TWO_COLUMNS_EMPTY
                return true
            }
        }
        return false
    }

    private fun clearDetails(): Boolean {
        supportFragmentManager.findFragmentByTag(TAG_DETAILS)?.let {
            supportFragmentManager
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .remove(it)
                    .commitAllowingStateLoss()
            return true
        }
        return false
    }
}
