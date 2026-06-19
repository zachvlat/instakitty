package com.zachvlat.instakitty.data.remote

class ConnectionResult(val success: Boolean, val message: String)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val type: String, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val message: String) : ApiResult<Nothing>()
}
