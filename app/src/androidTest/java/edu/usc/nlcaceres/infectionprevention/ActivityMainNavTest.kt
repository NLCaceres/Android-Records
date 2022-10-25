package edu.usc.nlcaceres.infectionprevention

import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test
import androidx.test.espresso.IdlingRegistry
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import edu.usc.nlcaceres.infectionprevention.robots.RoboTest
import edu.usc.nlcaceres.infectionprevention.util.EspressoIdlingResource
import edu.usc.nlcaceres.infectionprevention.util.RepositoryModule
import edu.usc.nlcaceres.infectionprevention.data.PrecautionRepository
import edu.usc.nlcaceres.infectionprevention.data.ReportRepository
import edu.usc.nlcaceres.infectionprevention.helpers.di.FakePrecautionRepository
import edu.usc.nlcaceres.infectionprevention.helpers.di.FakeReportRepository
import org.junit.After
import org.junit.Before

// Best to remember: Turn off animations for instrumentedTests via devOptions in "About Emulated Device" (tap buildNum 10 times)
// Settings > devOptions > Drawing section > Turn off windowAnimationScale, transitionAnimationScale, animationDurationScale
// Also can reduce animation durations to 1 for debug build

/* Tests MainActivity navigation interactions around the rest of the app */
@UninstallModules(RepositoryModule::class)
@HiltAndroidTest
class ActivityMainNavTest: RoboTest() {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)
  @get:Rule(order = 1) // This rule runs launch(ActivityClass) & can access the activity via this prop instead!
  val scenarioRule = ActivityScenarioRule(ActivityMain::class.java)

  @BindValue @JvmField // Each test gets its own version of the repo so no variable pollution like the closures
  var precautionRepository: PrecautionRepository = FakePrecautionRepository().apply { populateList() }
  @BindValue @JvmField
  var reportRepository: ReportRepository = FakeReportRepository().apply { populateList() }

  @Before
  fun registerIdlingResource() {
    IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource)
  }
  @After
  fun unregisterIdlingResource() {
    IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource)
  }

  @Test fun clickHealthPracticeToLaunchCreateActivity() {
      mainActivity {
        checkViewLoaded()
        goCreateIsoReportLabeled("Contact Enteric")
      }
      createReportActivity {
        checkCorrectTitle("New Contact Enteric Observation")
      }
  }

  @Test fun clickNavDrawerReportButtonToLaunchReportListActivity() {
      mainActivity {
        checkNavDrawerOpen(false) // Not open
        openNavDrawer()
        checkNavDrawerOpen(true) // Now Open
        goToReportList()
      }
      reportListActivity { // Verify in reportList (have to wait until RV loads)
        checkInitListLoaded("Hand Hygiene", "John Smith", "May 18")
      }
  }

  // Next 2 tests fail w/out Hilt stubs since backend currently DOESN'T send precautionTypes w/ reports
  @Test fun clickNavDrawerStandardReportFilterToLaunchReportListActivity() {
      mainActivity {
        checkNavDrawerOpen(false) // Not open
        openNavDrawer()
        checkNavDrawerOpen(true) // Now Open
        goToFilteredStandardReportList()
      }
      reportListActivity {
        checkFiltersLoaded("Standard")
        checkListCount(3)
      }
  }
  @Test fun clickNavDrawerIsoReportFilterToLaunchReportListActivity() {
      mainActivity {
        checkNavDrawerOpen(false) // Not open
        openNavDrawer()
        checkNavDrawerOpen(true) // Now Open
        goToFilteredIsolationReportList()
      }
      reportListActivity {
        checkFiltersLoaded("Isolation")
        checkListCount(2)
      }
  }

  @Test fun clickSettingsToolbarButtonToLaunchSettingsActivity() {
    mainActivity {
      checkNavDrawerOpen(false) // Not open
      goToSettings()
    }
    settingsActivity { checkInitLoad() }
  }
}