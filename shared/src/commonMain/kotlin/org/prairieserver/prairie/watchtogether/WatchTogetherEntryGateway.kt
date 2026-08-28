package org.prairieserver.prairie.watchtogether

import org.prairieserver.prairie.model.watchtogether.CreateRoomRequest
import org.prairieserver.prairie.model.watchtogether.JoinRoomRequest
import org.prairieserver.prairie.model.watchtogether.RoomResponse
import org.prairieserver.prairie.model.watchtogether.RoomSnapshot
import org.prairieserver.prairie.model.watchtogether.SetSelectionRequest
import org.prairieserver.prairie.network.ApiResult
import kotlinx.coroutines.flow.StateFlow

interface WatchTogetherEntryGateway {
    val roomSnapshot: StateFlow<RoomSnapshot?>
    suspend fun createRoom(request: CreateRoomRequest): ApiResult<RoomResponse>
    suspend fun joinRoom(request: JoinRoomRequest): ApiResult<RoomResponse>
    suspend fun setSelection(request: SetSelectionRequest): ApiResult<RoomResponse>
}
