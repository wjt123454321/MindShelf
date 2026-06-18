package com.example.mindshelf.data.repository

/** 登录态失效（token 过期且刷新失败）。 */
class SessionExpiredException : Exception("登录已过期，请重新登录")
