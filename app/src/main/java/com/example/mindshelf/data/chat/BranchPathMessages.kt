package com.example.mindshelf.data.chat

import com.example.mindshelf.data.remote.dto.BranchDto
import com.example.mindshelf.data.remote.dto.MessageDto

/** 按分支 fork 点回溯祖先链，再拼接本分支消息，形成完整对话路径。 */
fun buildBranchPathMessages(
    allMessages: List<MessageDto>,
    branchId: String,
    branches: List<BranchDto>,
): List<MessageDto> {
    if (allMessages.isEmpty()) return emptyList()
    val byId = allMessages.associateBy { it.id }
    val branchMsgs = allMessages.filter { it.branchId == branchId }.sortedBy { it.createdAt }
    val branch = branches.find { it.id == branchId } ?: return branchMsgs

    val ancestors = mutableListOf<MessageDto>()
    var currentId = branch.rootMessageId
    while (currentId != null) {
        val msg = byId[currentId] ?: break
        ancestors.add(0, msg)
        currentId = msg.parentId
    }
    return (ancestors + branchMsgs).distinctBy { it.id }.sortedBy { it.createdAt }
}
