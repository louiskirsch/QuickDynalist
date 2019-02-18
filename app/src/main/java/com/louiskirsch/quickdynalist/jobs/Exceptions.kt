package com.louiskirsch.quickdynalist.jobs

class BackendException(message: String): Exception(message)
class NoInboxException: Exception()
class ItemIdUnavailableException: Exception()
class InvalidJobException(message: String): Exception(message)
class NotAuthenticatedException: Exception()
