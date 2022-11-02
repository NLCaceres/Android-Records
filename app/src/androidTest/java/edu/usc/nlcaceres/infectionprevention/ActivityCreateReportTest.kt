package edu.usc.nlcaceres.infectionprevention

import androidx.test.espresso.IdlingRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import edu.usc.nlcaceres.infectionprevention.helpers.util.tapBackButton
import edu.usc.nlcaceres.infectionprevention.robots.RoboTest
import edu.usc.nlcaceres.infectionprevention.util.EspressoIdlingResource
import edu.usc.nlcaceres.infectionprevention.util.RepositoryModule
import edu.usc.nlcaceres.infectionprevention.data.PrecautionRepository
import edu.usc.nlcaceres.infectionprevention.data.ReportRepository
import edu.usc.nlcaceres.infectionprevention.helpers.di.FakePrecautionRepository
import edu.usc.nlcaceres.infectionprevention.helpers.di.FakeReportRepository
import org.junit.After
import org.junit.Test
import org.junit.Before
import org.junit.Rule

// @RunWith(AndroidJUnit4.class) // Not needed if set to default in build.gradle
@UninstallModules(RepositoryModule::class)
@HiltAndroidTest
class ActivityCreateReportTest: RoboTest() {
  @get:Rule(order = 0) // Best to start from MainActivity for a normal user Task experience
  val hiltRule = HiltAndroidRule(this)
  @get:Rule(order = 1)
  val scenarioRule = ActivityScenarioRule(ActivityMain::class.java)

  @BindValue @JvmField // Each test gets its own version of the repo so no variable pollution like the closures
  var precautionRepository: PrecautionRepository = FakePrecautionRepository().apply { populateList() }
  @BindValue @JvmField
  var reportRepository: ReportRepository = FakeReportRepository().apply { populateList() }

  @Before
  fun navigate_To_Create_Report_Activity() {
    IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource)
    mainActivity {
      checkViewLoaded()
      goCreateStandardReportLabeled("Hand Hygiene")
    }
    createReportActivity {
      checkCorrectTitle("New Hand Hygiene Observation")
      checkSpinnersLoaded()
    }
  }
  @After
  fun unregister_Idling_Resource() {
      IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource)
  }

  // Checking Initial Loading
  @Test fun check_Default_Header() { // Double Check!
    createReportActivity { checkCorrectTitle("New Hand Hygiene Observation") }
  }

  // Navigation
  @Test fun handle_Back_Navigation() { // May fail if not coming from mainActivity (which in this case is just closing app)
    tapBackButton()
    mainActivity { checkViewLoaded() }
  }
  @Test fun handle_Up_Navigation() {
    createReportActivity { pressUpButton() }
    mainActivity { checkViewLoaded() }
  }

  // Time and Date
  @Test fun select_Time() {
    createReportActivity {
      openTimeDialog()
      setTime(15, 24) // 3PM 24 minutes
      pressOkButton() // Goes to dateDialog
      pressCancelButton() // Cancel setting date half
      checkTimeDateET("3:24 PM") // BUT editText still updated!
    }
  }
  @Test fun select_Time_And_Date() {
    createReportActivity {
      openTimeDialog()
      setTime(15, 24) // 3PM 24 minutes
      pressOkButton() // Opens dateDialog
      setDate(2021, 4, 12) // Set it to 2021, MARCH (0 indexed month), 12 (1 index day)
      pressOkButton() // Finalize date
      checkTimeDateET("3:24 PM 4/12/2021") // American date
    }
  }

  // Spinners
  @Test fun select_Employee() {
    createReportActivity {
      openEmployeeSpinner()
      selectEmployee("Nicholas Caceres")
      checkSelectedEmployee("Nicholas Caceres")
    }
  }
  @Test fun select_Health_Practice() {
    createReportActivity {
      openHealthPracticeSpinner()
      selectHealthPractice("Droplet")
      checkSelectedHealthPractice("Droplet")
      checkCorrectTitle("New Droplet Observation")
    }
  }
  @Test fun select_Location() {
    createReportActivity {
      openFacilitySpinner()
      selectFacility("USC")
      checkSelectedFacility("USC 2 123")
    }
  }

  // Finalizing New Report
  @Test fun submit_Without_Date() {
    createReportActivity {
      pressSubmitButton() // Should open dialog since no date selected
      checkAlertDialog()
      pressCancelButton()
      checkSnackBar() // Cancel button opens snackbar telling user to set time/date
    }
  }
  @Test fun submit_With_Date() {
    createReportActivity {
      openTimeDialog()
      setTime(5, 45) // 3PM 24 minutes
      pressOkButton() // Opens dateDialog
      setDate(2019, 2, 24) // Set it
      pressOkButton() // Finalize date
      checkTimeDateET("5:45 AM 2/24/2019") // American date
      pressSubmitButton()
    }
    // Technically goes through mainActivity to get to reportList BUT
    // Android seems to optimize around actually needing to nav to mainActivity
    // Instead, just running the resultHandler that launches the intent toward the reportList view
    reportListFragment { // Should be no filters BUT should be a list of reports!
      checkInitListLoaded("Hand Hygiene", "John Smith", "May 18")
    }
  }
}