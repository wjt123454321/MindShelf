package com.example.mindshelf.data.remote

import com.example.mindshelf.data.remote.dto.ApiResponse
import com.example.mindshelf.data.remote.dto.AuthResultDto
import com.example.mindshelf.data.remote.dto.BranchDto
import com.example.mindshelf.data.remote.dto.ConversationDto
import com.example.mindshelf.data.remote.dto.CreateBranchRequest
import com.example.mindshelf.data.remote.dto.CreateConversationRequest
import com.example.mindshelf.data.remote.dto.CreateKbRequest
import com.example.mindshelf.data.remote.dto.CreateNoteRequest
import com.example.mindshelf.data.remote.dto.CreatePageRequest
import com.example.mindshelf.data.remote.dto.CustomPageDto
import com.example.mindshelf.data.remote.dto.UpdatePageRequest
import com.example.mindshelf.data.remote.dto.KnowledgeBaseDto
import com.example.mindshelf.data.remote.dto.LoginCodeRequest
import com.example.mindshelf.data.remote.dto.LoginRequest
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.remote.dto.NoteDto
import com.example.mindshelf.data.remote.dto.RefreshRequest
import com.example.mindshelf.data.remote.dto.RegisterRequest
import com.example.mindshelf.data.remote.dto.SendCodeRequest
import com.example.mindshelf.data.remote.dto.TokenRefreshResultDto
import com.example.mindshelf.data.remote.dto.UpdateKbRequest
import com.example.mindshelf.data.remote.dto.CreateShareLinkRequest
import com.example.mindshelf.data.remote.dto.NoteVersionDto
import com.example.mindshelf.data.remote.dto.ShareLinkDto
import com.example.mindshelf.data.remote.dto.SyncPullData
import com.example.mindshelf.data.remote.dto.SyncPushRequest
import com.example.mindshelf.data.remote.dto.SyncPushResult
import com.example.mindshelf.data.remote.dto.SyncResolveRequest
import com.example.mindshelf.data.remote.dto.TrashItemDto
import com.example.mindshelf.data.remote.dto.TrashRestoreRequest
import com.example.mindshelf.data.remote.dto.UpdateNoteRequest
import com.example.mindshelf.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MindShelfApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<AuthResultDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<AuthResultDto>

    @POST("auth/send-code")
    suspend fun sendCode(@Body body: SendCodeRequest): ApiResponse<Map<String, Int>>

    @POST("auth/login/code")
    suspend fun loginWithCode(@Body body: LoginCodeRequest): ApiResponse<AuthResultDto>

    @GET("auth/me")
    suspend fun me(): ApiResponse<UserDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiResponse<TokenRefreshResultDto>

    @GET("notes")
    suspend fun listNotes(): ApiResponse<List<NoteDto>>

    @POST("notes")
    suspend fun createNote(@Body body: CreateNoteRequest): ApiResponse<NoteDto>

    @GET("notes/{id}")
    suspend fun getNote(@Path("id") id: String): ApiResponse<NoteDto>

    @PATCH("notes/{id}")
    suspend fun updateNote(@Path("id") id: String, @Body body: UpdateNoteRequest): ApiResponse<NoteDto>

    @DELETE("notes/{id}")
    suspend fun deleteNote(@Path("id") id: String)

    @GET("knowledge-bases")
    suspend fun listKnowledgeBases(): ApiResponse<List<KnowledgeBaseDto>>

    @POST("knowledge-bases")
    suspend fun createKnowledgeBase(@Body body: CreateKbRequest): ApiResponse<KnowledgeBaseDto>

    @PATCH("knowledge-bases/{id}")
    suspend fun updateKnowledgeBase(
        @Path("id") id: String,
        @Body body: UpdateKbRequest,
    ): ApiResponse<KnowledgeBaseDto>

    @DELETE("knowledge-bases/{id}")
    suspend fun deleteKnowledgeBase(@Path("id") id: String)

    @GET("pages")
    suspend fun listPages(): ApiResponse<List<CustomPageDto>>

    @POST("pages")
    suspend fun createPage(@Body body: CreatePageRequest): ApiResponse<CustomPageDto>

    @GET("pages/{id}")
    suspend fun getPage(@Path("id") id: String): ApiResponse<CustomPageDto>

    @PATCH("pages/{id}")
    suspend fun updatePage(@Path("id") id: String, @Body body: UpdatePageRequest): ApiResponse<CustomPageDto>

    @DELETE("pages/{id}")
    suspend fun deletePage(@Path("id") id: String)

    @GET("conversations")
    suspend fun listConversations(): ApiResponse<List<ConversationDto>>

    @POST("conversations")
    suspend fun createConversation(@Body body: CreateConversationRequest): ApiResponse<ConversationDto>

    @DELETE("conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: String)

    @GET("conversations/{id}/branches")
    suspend fun listBranches(@Path("id") conversationId: String): ApiResponse<List<BranchDto>>

    @POST("conversations/{id}/branches")
    suspend fun createBranch(
        @Path("id") conversationId: String,
        @Body body: CreateBranchRequest,
    ): ApiResponse<BranchDto>

    @GET("conversations/{id}/messages")
    suspend fun listAllMessages(@Path("id") conversationId: String): ApiResponse<List<MessageDto>>

    @GET("conversations/{cid}/branches/{bid}/messages")
    suspend fun listMessages(
        @Path("cid") conversationId: String,
        @Path("bid") branchId: String,
    ): ApiResponse<List<MessageDto>>

    @POST("ai/search")
    suspend fun webSearch(@Body body: Map<String, String>): ApiResponse<Map<String, Any?>>

    @GET("notes/{id}/versions")
    suspend fun listNoteVersions(@Path("id") noteId: String): ApiResponse<List<NoteVersionDto>>

    @GET("notes/{noteId}/versions/{versionId}")
    suspend fun getNoteVersion(
        @Path("noteId") noteId: String,
        @Path("versionId") versionId: String,
    ): ApiResponse<NoteVersionDto>

    @POST("notes/{noteId}/versions/{versionId}/restore")
    suspend fun restoreNoteVersion(
        @Path("noteId") noteId: String,
        @Path("versionId") versionId: String,
    ): ApiResponse<NoteDto>

    @GET("trash")
    suspend fun listTrash(): ApiResponse<List<TrashItemDto>>

    @POST("trash/restore")
    suspend fun restoreTrash(@Body body: TrashRestoreRequest): ApiResponse<Any>

    @DELETE("trash/{entityType}/{id}")
    suspend fun purgeTrash(
        @Path("entityType") entityType: String,
        @Path("id") id: String,
    )

    @POST("share/links")
    suspend fun createShareLink(@Body body: CreateShareLinkRequest): ApiResponse<ShareLinkDto>

    @GET("share/links")
    suspend fun listShareLinks(): ApiResponse<List<ShareLinkDto>>

    @DELETE("share/links/{linkId}")
    suspend fun revokeShareLink(@Path("linkId") linkId: String)

    @GET("sync/pull")
    suspend fun syncPull(@Query("since") since: Long? = null): ApiResponse<SyncPullData>

    @POST("sync/push")
    suspend fun syncPush(@Body body: SyncPushRequest): ApiResponse<SyncPushResult>

    @POST("sync/resolve")
    suspend fun syncResolve(@Body body: SyncResolveRequest): ApiResponse<Any>
}
