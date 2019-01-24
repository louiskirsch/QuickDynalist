package com.louiskirsch.quickdynalist

class AuthenticatedEvent(val success: Boolean)
class ItemEvent(val success: Boolean)
class NoInboxEvent
class SyncEvent(val success: Boolean, val requiredUnmeteredNetwork: Boolean)
