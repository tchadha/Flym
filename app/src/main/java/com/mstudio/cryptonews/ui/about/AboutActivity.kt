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

package com.mstudio.cryptonews.ui.about

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.mstudio.cryptonews.R
import com.mstudio.cryptonews.utils.setupTheme
import com.vansuita.materialabout.builder.AboutBuilder


class AboutActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		setupTheme()

		super.onCreate(savedInstanceState)

		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		val view = AboutBuilder.with(this)
				.setPhoto(R.mipmap.profile_picture)
				.setCover(R.mipmap.profile_cover)
				.setName("Frédéric Julian")
				.setBrief("I'm publishing this application as free and open-source software under GPLv3 licence. Feel free to modify it as long as you keep it open-source as well.")
				.setAppIcon(R.mipmap.ic_launcher_foreground)
				.setAppName(R.string.app_name)
				.addGitHubLink("FredJul")
				.addFiveStarsAction()
				.addShareAction(R.string.app_name)
				.setWrapScrollView(true)
				.setLinksAnimated(true)
				.setShowAsCard(true)
				.build()

		setContentView(view)
	}

	override fun onOptionsItemSelected(item: MenuItem?): Boolean {
		when (item?.itemId) {
			android.R.id.home -> onBackPressed()
		}

		return true
	}
}