package eu.szwiec.checkittravelkit.repository

import android.content.Context
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import eu.szwiec.checkittravelkit.R
import eu.szwiec.checkittravelkit.prefs.Preferences
import eu.szwiec.checkittravelkit.repository.data.Country
import eu.szwiec.checkittravelkit.repository.data.Rate
import eu.szwiec.checkittravelkit.repository.local.CountriesJsonReader
import eu.szwiec.checkittravelkit.repository.local.CountryDao
import eu.szwiec.checkittravelkit.repository.remote.ApiErrorResponse
import eu.szwiec.checkittravelkit.repository.remote.ApiSuccessResponse
import eu.szwiec.checkittravelkit.repository.remote.CurrencyConverterService
import eu.szwiec.checkittravelkit.repository.remote.SherpaService
import eu.szwiec.checkittravelkit.util.AppExecutors
import timber.log.Timber
import java.util.concurrent.TimeUnit

interface CountryRepository {
    fun setup()
    fun getCountryNames(): LiveData<List<String>>
    fun getCountry(name: String): LiveData<Country>
}

class CountryRepositoryImpl(
        private val context: Context,
        private val appExecutors: AppExecutors,
        private val dao: CountryDao,
        private val jsonReader: CountriesJsonReader,
        private val sherpaService: SherpaService,
        private val currencyConverterService: CurrencyConverterService,
        private val preferences: Preferences
) : CountryRepository {

    override fun setup() {
        appExecutors.diskIO().execute {
            if (dao.countCountries() == 0) {
                val countries = jsonReader.getCountries()
                dao.bulkInsert(countries)
            }
        }
    }

    override fun getCountryNames(): LiveData<List<String>> {
        return dao.getNames()
    }

    override fun getCountry(name: String): LiveData<Country> {
        val result = MediatorLiveData<Country>()

        val originSource = dao.findByName(preferences.origin)
        val countrySource = dao.findByName(name)

        val dbSource = dao.findByName(name)

        result.addSource(countrySource) { country ->
            result.removeSource(countrySource)
            result.value = country

            result.addSource(originSource) { origin ->
                result.removeSource(originSource)

                val currencyConverterSource = currencyConverterService.convert(currencyFromTo(origin, country), "y")
                val visaSource = sherpaService.visaRequirements(auth(), visaFromTo(origin, country))

                if (shouldFetchRate(country)) {
                    result.addSource(currencyConverterSource) { response ->
                        result.removeSource(currencyConverterSource)
                        when (response) {
                            is ApiSuccessResponse -> {
                                val rate = Rate(response.body.value, origin.currency.code, System.currentTimeMillis())
                                val newCountry = country.update(rate)
                                appExecutors.diskIO().execute { dao.update(newCountry) }
                            }
                            is ApiErrorResponse -> {
                                Timber.w("Problem with fetching currency rate: %s", response.errorMessage)
                            }
                        }
                    }
                }

                if (shouldFetchVisa(country)) {
                    result.addSource(visaSource) { response ->
                        result.removeSource(currencyConverterSource)
                        when (response) {
                            is ApiSuccessResponse -> {
                                val newCountry = country.update(response.body)
                                appExecutors.diskIO().execute { dao.update(newCountry) }
                            }
                            is ApiErrorResponse -> {
                                Timber.w("Problem with fetching visa info: %s", response.errorMessage)
                            }
                        }
                    }
                }
            }
        }

        result.addSource(dbSource) { country ->
            result.value = country
        }

        return result
    }

    private fun shouldFetchVisa(country: Country): Boolean {
        val now = System.currentTimeMillis()
        val lastUpdate = country.visa.lastUpdate
        val oneDay = TimeUnit.DAYS.toMillis(1)

        return now - lastUpdate > oneDay
    }

    private fun shouldFetchRate(country: Country): Boolean {
        val now = System.currentTimeMillis()
        val lastUpdate = country.currency.rate.lastUpdate
        val oneMonth = TimeUnit.DAYS.toMillis(30)

        return now - lastUpdate > oneMonth
    }

    private fun currencyFromTo(from: Country, to: Country): String {
        return "${from.currency.code}_${to.currency.code}"
    }

    private fun visaFromTo(from: Country, to: Country): String {
        return "${from.id}-${to.id}"
    }

    private fun auth(): String {
        val username = context.getString(R.string.sherpa_username)
        val password = context.getString(R.string.sherpa_password)
        val credentials = "$username:$password"
        val basic = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        return basic
    }

}
