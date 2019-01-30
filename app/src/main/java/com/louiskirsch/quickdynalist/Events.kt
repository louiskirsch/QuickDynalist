package com.louiskirsch.quickdynalist

import com.louiskirsch.quickdynalist.objectbox.DynalistItem

class AuthenticatedEvent(val success: Boolean)
class ItemEvent(val success: Boolean)
class NoInboxEvent
class SyncEvent(val success: Boolean, val requiredUnmeteredNetwork: Boolean)
class RateLimitDelay(val delay: Long, val jobTag: String)
class DynalistLocateEvent(val item: DynalistItem)