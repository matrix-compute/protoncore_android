/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.key.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.proton.core.data.db.BaseDao
import me.proton.core.key.data.entity.PublicAddressKeyEntity

@Dao
abstract class PublicAddressKeyDao : BaseDao<PublicAddressKeyEntity>() {

    @Query("SELECT * FROM PublicAddressKeyEntity WHERE email = :email")
    abstract fun findAllByEmail(email: String): Flow<List<PublicAddressKeyEntity>>

    @Query("DELETE FROM PublicAddressKeyEntity WHERE email = :email")
    abstract suspend fun deleteByEmail(email: String)

    @Query("DELETE FROM PublicAddressKeyEntity")
    abstract suspend fun deleteAll()

}