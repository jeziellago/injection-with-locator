
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProviders
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.OnLifecycleEvent
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import java.lang.IllegalArgumentException


//region Setup
fun initAppInjection(setup: Module.() -> Unit): DependencyLocator {
    return AppLocator.apply { setup(appModule) }
}

fun initModuleInjection(
        lifecycleOwner: LifecycleOwner,
        setup: Module.() -> Unit
) {
    setup(ModuleLocator.module)
    lifecycleOwner.lifecycle.addObserver(ModuleLocator)
}
//endregion Setup

//region ViewModel
inline fun <reified T : ViewModel> LifecycleOwner.viewModelInject() = lazy {
    val providers = when (this) {
        is Fragment -> ViewModelProviders.of(this, viewModelFactory())
        is FragmentActivity -> ViewModelProviders.of(this, viewModelFactory())
        else -> throw IllegalArgumentException("LifecycleOwner must be Fragment or FragmentActivity")
    }
    providers.get(T::class.java)
}

inline fun <reified T : ViewModel> sharedViewModel() = lazy {
    ModuleLocator.module.getInstance(T::class.java)
}

fun viewModelFactory() = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ModuleLocator.module.getViewModel(modelClass)
    }
}
//endregion ViewModel

//region Instances Provider
inline fun <reified T> inject() = lazy {
    ModuleLocator.module.getInstanceOrNull(T::class.java)
            ?: AppLocator.appModule.getInstanceOrNull(T::class.java)
            ?: throw UninitializedPropertyAccessException("${T::class.java} not injected.")
}

private class Injector {

    private val instances by lazy { mutableSetOf<Any>() }

    fun inject(instance: Any) = instances.add(instance)

    fun <T : ViewModel> provideViewModel(viewModelClass: Class<T>) = provide(viewModelClass)

    fun <T> provide(clazz: Class<T>): T {
        return provideOrNull(clazz)
                ?: throw UninitializedPropertyAccessException("$clazz not provided.")
    }

    fun <T> provideOrNull(clazz: Class<T>) = instances.filterIsInstance(clazz).firstOrNull()

    fun clearInstances() = instances.clear()
}
//endregion Injection

//region Locator
object AppLocator : DependencyLocator {
    val appModule by lazy { Module() }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    override fun onDestroy() = appModule.onDestroy()
}

object ModuleLocator : DependencyLocator {
    val module by lazy { Module() }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    override fun onDestroy() = module.onDestroy()
}

interface DependencyLocator : LifecycleObserver {
    fun onDestroy()
}
//endregion Locator

//region Module
class Module {

    private val injector by lazy { Injector() }

    fun inject(instance: Any) {
        injector.inject(instance)
    }

    inline fun <reified T> get(): T = getInstance(T::class.java)

    fun <T> getInstance(clazz: Class<T>) = injector.provide(clazz)

    fun <T> getInstanceOrNull(clazz: Class<T>) = injector.provideOrNull(clazz)

    fun <T : ViewModel> getViewModel(viewModelClass: Class<T>) =
            injector.provideViewModel(viewModelClass)

    fun onDestroy() {
        injector.clearInstances()
    }

}
//endregion Module
