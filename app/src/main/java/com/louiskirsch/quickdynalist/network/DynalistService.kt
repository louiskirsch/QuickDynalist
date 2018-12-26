package com.louiskirsch.quickdynalist.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded


interface DynalistService {
    @POST("file/list")
    fun listFiles(@Body request: AuthenticatedRequest): Call<DynalistResponse>

    @POST("inbox/add")
    fun addToInbox(@Body request: InboxRequest): Call<DynalistResponse>
}

class AuthenticatedRequest(val token: String)
class InboxRequest(val content: String, val token: String)

class DynalistResponse {
    val _code: String? = null

    val isInvalidToken: Boolean
        get() = _code == "InvalidToken"

    val isOK: Boolean
        get() = _code == "Ok"
}

