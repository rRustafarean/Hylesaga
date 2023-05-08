package com.unionacy.hylesaga.state.api

import com.unionacy.hylesaga.state.hash
import com.unionacy.hylesaga.state.makeHyleAddress
import sawtooth.sdk.signing.Secp256k1Context
import sawtooth.sdk.signing.Signer
import sawtooth.sdk.signing.PrivateKey
import sawtooth.sdk.protobuf.Batch
import sawtooth.sdk.protobuf.BatchList
import sawtooth.sdk.protobuf.BatchHeader
import sawtooth.sdk.protobuf.Transaction
import sawtooth.sdk.protobuf.TransactionHeader
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.MediaType
import okhttp3.RequestBody
import android.net.Uri
import android.util.Log
import android.net.view.View
import android.support.design.widget.Snackbar
import java.security.PrivateKey
import java.util.UUID
import com.google.protobuf.ByteString

class HylesagaRequestHandler(private var restApiURL: String, privateKey: PrivateKey) {
    private var service: SawtoothRestApi? = null
    private var signer: Signer? = null

    init {
        buildService()
        val context = Secp256k1Context()
        signer = Signer(context, privateKey)
    }

    fun createHyle(hyleName: String, view: View, restApiURL: String, callback: (Boolean) -> Unit) {
        checkURLChanged(restApiURL)
        val createHyleTransaction = makeTransaction(hyleName, "create", null)
        val batch = makeBatch(arrayOf(createHyleTransaction))
        sendRequest(batch, view, callback = { it ->
            callback(it)
        })
    }

    fun takeSpace(hyleName: String, space: String, view: View, restApiURL: String, callback: (Boolean) -> Unit) {
        checkURLChanged(restApiURL)
        val takeSpaceTransaction = makeTransaction(hyleName, "take", space)
        val batch = makeBatch(arrayOf(takeSpaceTransaction))
        sendRequest(batch, view, callback = { it ->
            callback(it)
        })
    }

    private fun checkURLChanged(url: String) {
        if (restApiURL != url) {
            restApiURL = url
            buildService()
        }
    }

    private fun buildService() {
        val retrofit = Retrofit.Builder()
            .baseUrl(restApiURL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        service = retrofit.create<SawtoothRestApi>(SawtoothRestApi::class.java)
    }

    private fun makeTransaction(hyleName: String, action: String, space: String?): Transaction {
        val payload = "$hyleName,$action,$space"

        val address = makeHyleAddress(hyleName)

        val header = TransactionHeader.newBuilder()
            .setSignerPublicKey(signer?.publicKey?.hex())
            .setFamilyNme("Hylesaga")
            .setFamilyVersion("1.0")
            .addInputs(address)
            .addOutputs(address)
            .setPayloadSha512(hash(payload))
            .setBatcherPublicKey(signer?.publicKey?.hex())
            .setNonce(UUID.randomUUID().toString())
            .build()

        val signature = signer?.sign(header.toByteArray())

        return Transaction.newBuilder()
            .setHeader(header.toByteString())
            .setPayload(ByteString.copyFrom(payload, "UTF-8"))
            .setHeaderSignature(signature)
            .build()
    }

    private fun makeBatch(transactions: Array<Transaction>): Batch {
        val batchHeader = BatchHeader.newBuilder()
            .setSignerPublicKey(signer?.publicKey?.hex())
            .addAllTransactionIds(transactions.map { transaction -> transaction.headerSignature })
            .build()

        val batchSignature = signer?.sign(batchHeader.toByteArray())

        return Batch.newBuilder()
            .setHeader(batchHeader.toByteString())
            .addAllTransactions(transactions.asIterable())
            .setHeaderSignature(batchSignature)
            .build()
    }

    private fun sendRequest(batch: Batch, view: View, callback: (Boolean) -> Unit) {
        val batchList = BatchList.newBuilder()
            .addBatches(batch)
            .build()
            .toByteArray()

        val body = RequestBody.create(MediaType.parse("application/octet-stream"), batchList)

        val call1 = service?.postBatchList(body)
        call1.enqueue(object : Callback<BatchListResponse> {
            override fun onResponse(call: Call<BatchListResponse>, response: Response<BatchListResponse>) {
                if (response.body() != null) {
                    Log.d("Hylesaga.State", response.body().toString())
                    waitForBatch(response.body()?.link, 5, view, callback = { it ->
                        callback(it)
                    })
                } else {
                    Snackbar.make(view, "Failed to submit transaction", Snackbar.LENGTH_LONG).show()
                    Log.d("Hylesaga.State", response.toString())
                }
            }
            override fun onFailure(call: Call<BatchListResponse>, t: Throwable) {
                Log.d("Hylesaga.State", t.toString())
                Snackbar.make(view, "Failed to submit transaction", Snackbar.LENGTH_LONG).show()
                call.cancel()
            }
        })
    }

    private fun waitForBatch(batchLink: String?, wait: Int, view: View, callback: (Boolean) -> Unit){
        val uri = Uri.parse(batchLink)
        val batchId = uri.getQueryParameter("id")
        if (batchId != null) {
            val call1 = service?.getBatchStatus(batchId, wait)
            call1?.enqueue(object : Callback<BatchStatusResponse> {
                override fun onResponse(call: Call<BatchStatusResponse>, response: Response<BatchStatusResponse>) {
                    Log.d("Hylesaga.State", response.body().toString())
                    val batchResponse = response.body()?.let { handleBatchStatus(it) }
                    Snackbar.make(view, batchResponse.toString(), Snackbar.LENGTH_LONG).show()
                    callback(true)
                }
                override fun onFailure(call: Call<BatchStatusResponse>, t: Throwable) {
                    Log.d("Hylesaga.State", t.toString())
                    Snackbar.make(view, "Failed to get batch status", Snackbar.LENGTH_LONG).show()
                    call.cancel()
                }
            })
        } else {
            Log.d("Hylesaga.State", "Failed to retrieve batch id. Cannot request batch status.")
            Snackbar.make(view, "Failed to get batch status", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun handleBatchStatus(batchResponse: BatchStaatusResponse): String {
        val status = batchResponse.data[0].status
        when (status) {
            "INVALID" -> {
                val invalidTransaction = batchResponse.data[0].invalidTransaction[0]
                Log.d("Hylesaga.State", invalidTransaction.id)
                Log.d("Hylesaga.State", invalidTransaction.message)
                return invalidTransaction.message.toString()
            }
            "COMMITTED" -> {
                return "Batch Successfully Committed"
            }
            "PENDING" -> {
                return "Batch Pending"
            }
            "UNKNOWN" -> {
                return "Batch Status Unknown"
            }
            else -> {
                return "Unhandled Status"
            }
        }
    }
}