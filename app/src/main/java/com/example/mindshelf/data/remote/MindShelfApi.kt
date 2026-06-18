package com.example.mindshelf.data.remote

import com.example.mindshelf.data.remote.dto.ApiResponse
import com.example.mindshelf.data.remote.dto.AuthResultDto
import com.example.mindshelf.data.remote.dto.BranchDto
import com.example.mindshelf.data.remote.dto.ChatStreamRequest
import com.example.mindshelf.data.remote.dto.ConversationDto
import com.example.mindshelf.data.remote.dto.CreateBranchRequest
import com.example.mindshelf.data.remote.dto.CreateConversationRequest
import com.example.mindshelf.data.remote.dto.CreateKbRequest
import com.example.mindshelf.data.remote.dto.CreateNoteRequest
import com.example.mindshelf.data.remote.dto.KnowledgeBaseDto
import com.example.mindshelf.data.remote.dto.LoginCodeRequest
import com.example.mindshelf.data.remote.dto.LoginRequest
import com.example.mindshelf.data.remote.dto.MessageDto
import com.example.mindshelf.data.remote.dto.NoteDto
import com.example.mindshelf.data.remote.dto.RefreshRequest
import com.example.mindshelf.data.remote.dto.RegisterRequest
import com.example.mindshelf.data.remote.dto.SendCodeRequest
import com.example.mindshelf.data.remote.dto.TokenRefreshResultDto
import com.example.mindshelf.data.remote.dto.ToolConfirmRequest
import com.example.mindshelf.data.remote.dto.ToolConfirmResultDto
import com.example.mindshelf.data.remote.dto.ToolResumeRequest
import com.example.mindshelf.data.remote.dto.PendingToolDto
import com.example.mindshelf.data.remote.dto.UpdateKbRequest
import com.example.mindshelf.data.remote.dto.UpdateNoteRequest
import com.example.mindshelf.data.remote.dto.UserDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

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

    @Streaming
    @POST("ai/chat/stream")
    suspend fun chatStream(
        @Header("Accept") accept: String = "text/event-stream",
        @Body body: ChatStreamRequest,
    ): ResponseBody

    @POST("ai/tools/confirm")
    suspend fun confirmTool(@Body body: ToolConfirmRequest): ApiResponse<ToolConfirmResultDto>

    @GET("ai/tools/pending")
    suspend fun listPendingTools(
        @retrofit2.http.Query("conversation_id") conversationId: String,
        @retrofit2.http.Query("branch_id") branchId: String? = null,
    ): ApiResponse<List<PendingToolDto>>

    @Streaming
    @POST("ai/tools/resume/stream")
    suspend fun resumeToolStream(
        @Header("Accept") accept: String = "text/event-stream",
        @Body body: ToolResumeRequest,
    ): ResponseBody
}
