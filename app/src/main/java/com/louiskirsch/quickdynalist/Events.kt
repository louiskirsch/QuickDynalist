package com.louiskirsch.quickdynalist

import com.louiskirsch.quickdynalist.jobs.Bookmark

class AuthenticatedEvent(val success: Boolean)
class ItemEvent(val success: Boolean)
class BookmarksUpdatedEvent(val newBookmarks: Array<Bookmark>)
class BookmarkContentsUpdatedEvent(val bookmark: Bookmark)
class NoInboxEvent
