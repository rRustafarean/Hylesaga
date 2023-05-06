package com.unionacy.hylesaga.state

import androidx.lifecycle.MutableLiveData
import com.unionacy.hylesaga.state.api.SawtoothRestApi
import com.unionacy.hylesaga.state.api.StateResponse
import com.unionacy.hylesaga.state.api.Entry
import retrofit2.Retrofit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import android.util.Log
import com.google.common.io.BaseEncoding

class HylesagaStateRepository(url: String) {
    private var service: SawtoothRestApi? = null
    var hyles: MutableLiveData<List<Hyle>> = MutableLiveData()
    var hyleFocus: MutableLiveData<Hyle> = MutableLiveData()
    private var restApiURL: String = url

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl(restApiURL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        service = retrofit.create<SawtoothRestApi>(SawtoothRestApi::class.java)
    }

    fun getState(update: Boolean, url: String) {
        checkURLChanged(url)
        val resp = arrayListOf<Hyle>()
        if (update) {
            service?.getState(transactionFamilyPrefix())?.enqueue(object: Callback<StateResponse> {
                override fun onResponse(call: Call<StateResponse>, response<StateResponse>) {
                    if (response.body() != null) {
                        response.body()?.data?.map { entry ->

                            resp.add(parseHyle(entry.data))
                        }
                        hyles.value = resp.sortedBy { it.name.toLowerCase() }

                        Log.d("Hylesaga.State", "Updated hyle list")
                    } else {
                        Log.d("Hylesaga.State", response.toString())
                    }
                }
                override fun onFailure(call: Call<StateResponse>, t: Throwable) {
                    Log.d("Hylesaga.State", t.toString())
                    call.cancel()
                }
            })
        }
    }

    fun getHyleState(name: String, url: String) {
        checkURLChanged(url)
        val hyleAddress = makeHyleAddress(name)
        service?.getState(hyleAddress)?.enqueue(object : Callback<StateResponse> {
            override fun onResponse(call: Call<StateResponse>, response: Response<StateResponse>) {
                if (response.body() != null) {
                    val entry: Entry? = response.body()?.data?.get(0)
                    val hyleData: Hyle = entry?.data?.let { parseHyle(it) }!!
                    hyleFocus.value = hyleData
                    Log.d("Hylesaga.State", "Updated hyle state")
                } else {
                    Log.d("Hylesaga.State", response.toString())
                }
            }
            override fun onFailure(call: Call<StateResponse>, t: Throwable) {
                Log.d("Hylesaga.State", t.toString())
                call.cancel()
            }
        })
    }

    private fun parseHyle(data: String): Hyle {
        val decoded = String(BaseEncoding.base64().decode(data))
        val split = decoded.split(',')
        return Hyle(split[0], split[1], split[2], split[3], split[4])
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
}