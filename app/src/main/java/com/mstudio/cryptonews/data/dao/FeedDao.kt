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

package com.mstudio.cryptonews.data.dao

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import com.mstudio.cryptonews.data.entities.Feed
import com.mstudio.cryptonews.data.entities.FeedWithCount

private const val ENTRY_COUNT = "(SELECT COUNT(*) FROM entries WHERE feedId IS f.feedId AND read = 0)"

@Dao
interface FeedDao {
    @get:Query("SELECT * FROM feeds WHERE isGroup = 0")
    val allNonGroupFeeds: List<Feed>

    @get:Query("SELECT * FROM feeds ORDER BY groupId DESC, displayPriority ASC, feedId ASC")
    val all: List<Feed>

    @get:Query("SELECT * FROM feeds ORDER BY groupId DESC, displayPriority ASC, feedId ASC")
    val observeAll: LiveData<List<Feed>>

    @get:Query("SELECT *, $ENTRY_COUNT AS entryCount FROM feeds AS f ORDER BY groupId DESC, displayPriority ASC, feedId ASC")
    val observeAllWithCount: LiveData<List<FeedWithCount>>

    @Query("SELECT * FROM feeds WHERE feedId IS :id LIMIT 1")
    fun findById(id: Long): Feed?

    @Query("SELECT * FROM feeds WHERE feedLink IS :link LIMIT 1")
    fun findByLink(link: String): Feed?

    @Query("UPDATE feeds SET retrieveFullText = 1 WHERE feedId = :feedId")
    fun enableFullTextRetrieval(feedId: Long)

    @Query("UPDATE feeds SET retrieveFullText = 0 WHERE feedId = :feedId")
    fun disableFullTextRetrieval(feedId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg feeds: Feed)

    @Update
    fun update(vararg feeds: Feed)

    @Delete
    fun delete(vararg feeds: Feed)
}