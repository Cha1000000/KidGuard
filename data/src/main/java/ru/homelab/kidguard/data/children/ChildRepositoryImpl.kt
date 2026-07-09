package ru.homelab.kidguard.data.children

import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.model.ChildWithCode
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.data.network.ChildDto
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.CoParentRequest
import ru.homelab.kidguard.data.network.CreateChildRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChildRepositoryImpl @Inject constructor(
    private val childrenApi: ChildrenApi
) : ChildRepository {

    override suspend fun createChild(name: String, avatar: Int): Result<ChildWithCode> = try {
        val response = childrenApi.createChild(CreateChildRequest(name, avatar))
        Result.success(ChildWithCode(response.child.toDomain(), response.code))
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun listChildren(): Result<List<Child>> = try {
        val response = childrenApi.listChildren()
        Result.success(response.children.map { it.toDomain() })
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun regeneratePairCode(childId: Int): Result<String> = try {
        Result.success(childrenApi.regeneratePairCode(childId).code)
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun inviteCoParent(childId: Int, email: String): Result<Boolean> = try {
        val response = childrenApi.inviteCoParent(childId, CoParentRequest(email))
        Result.success(response.status == "linked")
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun ChildDto.toDomain() = Child(id = id, name = name, avatar = avatar, paired = paired)
}
