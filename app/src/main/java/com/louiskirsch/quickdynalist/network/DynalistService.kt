package com.louiskirsch.quickdynalist.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call


interface DynalistService {
    @POST("file/list")
    fun listFiles(@Body request: AuthenticatedRequest): Call<FilesResponse>

    @POST("inbox/add")
    fun addToInbox(@Body request: InboxRequest): Call<DynalistResponse>

    @POST("doc/read")
    fun readDocument(@Body request: ReadDocumentRequest): Call<DocumentResponse>

    @POST("doc/edit")
    fun addToDocument(@Body request: InsertItemRequest): Call<DynalistResponse>
}

class AuthenticatedRequest(val token: String)
class InboxRequest(val content: String, val note: String, val token: String)
class ReadDocumentRequest(val file_id: String, val token: String)

class InsertItemRequest(val file_id: String, parent_id: String,
                        content: String, note: String, val token: String) {

    class InsertSpec(val parent_id: String, val content: String, val note: String) {
        val action: String = "insert"
        val index: Int = -1
    }

    val changes = arrayOf(InsertSpec(parent_id, content, note))
}

open class DynalistResponse {
    val _code: String? = null

    val isInvalidToken: Boolean
        get() = _code == "InvalidToken"

    val isInboxNotConfigured: Boolean
        get() = _code == "NoInbox"

    val isOK: Boolean
        get() = _code == "Ok"
}

class File {
    val id: String? = null
    val title: String? = null
    val type: String? = null
    val permission: Int? = null

    val isEditable: Boolean
        get() = permission!! >= 2

    val isDocument: Boolean
        get() = type == "document"
}

class FilesResponse: DynalistResponse() {
    val files: List<File>? = null
}

class Node(val id: String, val content: String, val note: String, val checked: Boolean,
           val collapsed: Boolean, val parent: String?, val children: List<String>?)

class DocumentResponse: DynalistResponse() {
    val nodes: List<Node>? = null
}
