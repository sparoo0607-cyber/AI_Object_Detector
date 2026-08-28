package com.accessibility.detector.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.accessibility.detector.R
import com.accessibility.detector.databinding.ActivityOnboardingBinding
import com.accessibility.detector.databinding.OnboardingPageBinding

/**
 * First-run introduction (KOBIL-style light onboarding). Shown once; every later launch
 * routes straight to [HomeActivity].
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private data class Page(val icon: Int, val title: Int, val body: Int)

    private val pages = listOf(
        Page(R.drawable.ic_shield, R.string.onb_welcome_title, R.string.onb_welcome_body),
        Page(R.drawable.ic_camera, R.string.onb_vision_title, R.string.onb_vision_body),
        Page(R.drawable.ic_sound, R.string.onb_sound_title, R.string.onb_sound_body),
        Page(R.drawable.ic_translate, R.string.onb_speak_title, R.string.onb_speak_body)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isOnboardingComplete(this)) {
            goHome()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyLightSystemBars()

        binding.pager.adapter = PageAdapter()
        buildDots()

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = render(position)
        })
        render(0)

        binding.btnSkip.setOnClickListener { finishOnboarding() }
        binding.btnNext.setOnClickListener {
            val next = binding.pager.currentItem + 1
            if (next < pages.size) binding.pager.currentItem = next else finishOnboarding()
        }
    }

    private fun render(position: Int) {
        val last = position == pages.size - 1
        binding.btnNext.setText(if (last) R.string.onb_start else R.string.onb_next)
        binding.btnSkip.visibility = if (last) View.INVISIBLE else View.VISIBLE
        for (i in 0 until binding.dotsContainer.childCount) {
            binding.dotsContainer.getChildAt(i).setBackgroundResource(
                if (i == position) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
    }

    private fun buildDots() {
        val gap = (resources.displayMetrics.density * 6).toInt()
        repeat(pages.size) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = gap
            lp.marginEnd = gap
            dot.layoutParams = lp
            dot.setBackgroundResource(R.drawable.dot_inactive)
            binding.dotsContainer.addView(dot)
        }
    }

    private fun applyLightSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun finishOnboarding() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
        goHome()
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val b: OnboardingPageBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(OnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = pages.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val page = pages[position]
            holder.b.imgIcon.setImageResource(page.icon)
            holder.b.tvTitle.setText(page.title)
            holder.b.tvBody.setText(page.body)
        }
    }

    companion object {
        private const val PREFS = "sahey_onboarding"
        private const val KEY_DONE = "onboarding_done"

        fun isOnboardingComplete(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
    }
}
