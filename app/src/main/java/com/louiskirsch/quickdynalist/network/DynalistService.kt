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

    @POST("doc/edit")
    fun addToDocument(@Body request: BulkInsertItemRequest): Call<DynalistResponse>

    @POST("doc/edit")
    fun moveItem(@Body request: MoveItemRequest): Call<DynalistResponse>

    @POST("doc/edit")
    fun deleteItem(@Body request: DeleteItemRequest): Call<DynalistResponse>

    @POST("doc/edit")
    fun editItem(@Body request: EditItemRequest): Call<DynalistResponse>
}

class AuthenticatedRequest(val token: String)
class InboxRequest(val content: String, val note: String, val token: String)
class ReadDocumentRequest(val file_id: String, val token: String)

class InsertItemRequest(val file_id: String, parent_id: String,
                        content: String, note: String, val token: String) {

    class InsertSpec(val parent_id: String, val content: String, val note: String,
                     val index: Int = -1) {
        val action: String = "insert"
    }

    val changes = arrayOf(InsertSpec(parent_id, content, note))
}

class BulkInsertItemRequest(val file_id: String, val token: String,
                            val changes: Array<InsertItemRequest.InsertSpec>)

class EditItemRequest(val file_id: String, node_id: String,
                      content: String, note: String, checked: Boolean, val token: String) {

    class EditSpec(val node_id: String, val content: String,
                   val note: String, val checked: Boolean) {
        val action: String = "edit"
    }

    val changes = arrayOf(EditSpec(node_id, content, note, checked))
}

class MoveItemRequest(val file_id: String, parent_id: String, node_id: String,
                      index: Int, val token: String) {

    class MoveSpec(val parent_id: String, val node_id: String, val index: Int) {
        val action: String = "move"
    }

    val changes = arrayOf(MoveSpec(parent_id, node_id, index))
}

class DeleteItemRequest(val file_id: String, node_id: String, val token: String) {

    class DeleteSpec(val node_id: String) {
        val action: String = "delete"
    }

    val changes = arrayOf(DeleteSpec(node_id))
}

open class DynalistResponse {
    val _code: String? = null
    val _msg: String? = null

    val isRateLimitExceeded: Boolean
        get() = _code == "TooManyRequests"

    val isInvalidToken: Boolean
        get() = _code == "InvalidToken"

    val isInboxNotConfigured: Boolean
        get() = _code == "NoInbox"

    val isRequestUnfulfillable: Boolean
        get() = _code in listOf("Unauthorized", "NotFound", "NodeNotFound")

    val isOK: Boolean
        get() = _code == "Ok"

    val errorDesc: String
        get() = "Code: $_code; Message: $_msg"
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
