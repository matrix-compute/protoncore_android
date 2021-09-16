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

package me.proton.core.contact.data.repository

import com.dropbox.android.external.store4.Fetcher
import com.dropbox.android.external.store4.SourceOfTruth
import com.dropbox.android.external.store4.StoreBuilder
import com.dropbox.android.external.store4.StoreRequest
import com.dropbox.android.external.store4.fresh
import com.dropbox.android.external.store4.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.proton.core.contact.data.api.ContactApi
import me.proton.core.contact.data.api.ContactApiHelper
import me.proton.core.contact.data.local.db.ContactDatabase
import me.proton.core.contact.domain.entity.Contact
import me.proton.core.contact.domain.entity.ContactEmail
import me.proton.core.contact.domain.entity.ContactId
import me.proton.core.contact.domain.entity.ContactWithCards
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.data.arch.toDataResult
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.arch.mapSuccess
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider

class ContactRepositoryImpl(
    private val provider: ApiProvider,
    private val database: ContactDatabase
) : ContactRepository {

    private val contactApiHelper = ContactApiHelper(provider)

    private data class ContactStoreKey(val userId: UserId, val contactId: ContactId)

    private val contactWithCardsStore = StoreBuilder.from(
        fetcher = Fetcher.of { key: ContactStoreKey ->
            provider.get<ContactApi>(key.userId).invoke {
                getContact(key.contactId.id).contact.toContactWithCards()
            }.valueOrThrow
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = { contactStoreKey -> database.getContact(contactStoreKey.contactId) },
            writer = { key, input -> database.mergeContactWithCards(key.userId, input) },
            delete = { key -> database.contactDao().deleteContact(key.contactId) },
            deleteAll = database.contactDao()::deleteAllContacts
        )
    ).build()

    private val allContactsStore = StoreBuilder.from(
        fetcher = Fetcher.of { userId: UserId ->
            contactApiHelper.getAllContacts(userId)
        },
        sourceOfTruth = SourceOfTruth.of(
            reader = database::getAllContacts,
            writer = database::mergeContacts,
            delete = database.contactDao()::deleteAllContacts,
            deleteAll = database.contactDao()::deleteAllContacts
        )
    ).build()

    override suspend fun getContactWithCards(
        sessionUserId: SessionUserId,
        contactId: ContactId,
        refresh: Boolean
    ): ContactWithCards {
        val key = ContactStoreKey(sessionUserId, contactId)
        return if (refresh) contactWithCardsStore.fresh(key) else contactWithCardsStore.get(key)
    }

    override fun getAllContactsFlow(sessionUserId: SessionUserId, refresh: Boolean): Flow<DataResult<List<Contact>>> {
        return allContactsStore.stream(StoreRequest.cached(sessionUserId, refresh)).map { it.toDataResult() }
    }

    override fun getAllContactEmailsFlow(
        sessionUserId: SessionUserId,
        refresh: Boolean
    ): Flow<DataResult<List<ContactEmail>>> {
        return getAllContactsFlow(sessionUserId, refresh).mapSuccess {  contactsResult ->
            DataResult.Success(
                source = contactsResult.source,
                value = contactsResult.value.flatMap { it.contactEmails }
            )
        }
    }
}
