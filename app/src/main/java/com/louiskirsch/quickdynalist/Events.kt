package com.louiskirsch.quickdynalist

import android.net.Uri
import com.louiskirsch.quickdynalist.objectbox.DynalistItem

class AuthenticatedEvent(val success: Boolean)
class ItemEvent(val success: Boolean)
class NoInboxEvent
class RateLimitDelay(val delay: Long, val jobTag: String)
class DynalistLocateEvent(val item: DynalistItem)
class ForbiddenImageEvent(val uri: Uri)

enum class SyncStatus { RUNNING, NOT_RUNNING, SUCCESS, NO_SUCCESS }
class SyncEvent(val status: SyncStatus, val isManual: Boolean)
