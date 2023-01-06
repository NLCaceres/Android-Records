package edu.usc.nlcaceres.infectionprevention

import android.util.Log
import android.os.Bundle
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.core.content.ContextCompat
import android.view.*
import android.widget.TextView
import android.widget.EditText
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.JustifyContent
import androidx.core.view.MenuProvider
import android.text.InputType.TYPE_CLASS_TEXT
import androidx.fragment.app.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI.onNavDestinationSelected
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import edu.usc.nlcaceres.infectionprevention.adapters.ReportAdapter
import edu.usc.nlcaceres.infectionprevention.adapters.SelectedFilterAdapter
import edu.usc.nlcaceres.infectionprevention.data.FilterItem
import edu.usc.nlcaceres.infectionprevention.databinding.FragmentReportListBinding
import edu.usc.nlcaceres.infectionprevention.viewModels.ViewModelReportList
import edu.usc.nlcaceres.infectionprevention.util.*

/* Lists all reports returned using a complex RecyclerView - Easily one of the most complex views
 Navigates to a Sorting/Filter View (FragmentSortFilter) on click of Floating Filter Button */
@AndroidEntryPoint
class FragmentReportList: Fragment(R.layout.fragment_report_list) {
  private val viewModel: ViewModelReportList by viewModels()
  private var _viewBinding: FragmentReportListBinding? = null
  private val viewBinding get() = _viewBinding!!

  private lateinit var refreshLayout : SwipeRefreshLayout
  private lateinit var sorryMsgTextView : TextView

  private lateinit var filterFloatButton : FloatingActionButton
  private lateinit var selectedFilterRV : RecyclerView
  private lateinit var selectedFilterAdapter : SelectedFilterAdapter

  private lateinit var reportsRV : RecyclerView
  private lateinit var reportsAdapter : ReportAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    EspressoIdlingResource.increment() // Decrement in onComplete block of viewModel
  }
  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    _viewBinding = FragmentReportListBinding.inflate(inflater, container, false)
    return viewBinding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    requireActivity().addMenuProvider(ReportListMenu(), viewLifecycleOwner, Lifecycle.State.RESUMED)

    setupRefresh()
    setupToastMessage()
    setupFloatButtonToSortFilterView()

    setupSortFilterViews()
    setupReportRV()

    setupSortFilterResultListener()
  }

  private fun setupRefresh() {
    refreshLayout = viewBinding.swipeLayout.apply {
      val colorMode = if (IsDarkMode(requireContext())) R.color.lightMode else R.color.darkMode
      setColorSchemeResources(R.color.colorPrimary, R.color.colorLight, colorMode)
      setOnRefreshListener { viewModel.refreshReportList() }
    }
    viewModel.isLoading.observe(viewLifecycleOwner) { loading -> refreshLayout.isRefreshing = loading }
  }
  private fun setupToastMessage() {
    sorryMsgTextView = viewBinding.sorryTextView // Fallback textview
    viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
      if (message.isNotBlank()) { // Can't be empty ("") or just whitespace ("   ")
        with(sorryMsgTextView) {
          visibility = if (viewModel.reportListEmpty()) View.VISIBLE else View.INVISIBLE
          text = message
        }
        setFragmentResult(SnackbarRequestKey, bundleOf(SnackbarMessageParcel to message))
      }
    }
  }
  private fun setupFloatButtonToSortFilterView() {
    filterFloatButton = viewBinding.sortFilterFloatingButton.apply { setOnClickListener {
      findNavController().navigate(R.id.actionToSortFilterFragment)
    }}
  }
  private fun setupSortFilterResultListener() {
    parentFragmentManager.setFragmentResultListener(SortFilterRequestKey, viewLifecycleOwner) { requestKey, result ->
      if (requestKey != SortFilterRequestKey) { return@setFragmentResultListener } // Early break if key is incorrect
      result.fetchParcelableList<FilterItem>(SelectedFilterParcel)?.let { newFiltersList -> // Make sure not null
        setFragmentResult(SnackbarRequestKey, bundleOf(SnackbarMessageParcel to "Filtering and Sorting!"))

        viewModel.selectedFilters.clear()
        viewModel.selectedFilters.addAll(newFiltersList) // Update filters for display
        selectedFilterAdapter.notifyItemRangeInserted(0, newFiltersList.size)

        reportsAdapter.submitList(viewModel.sortedFilteredList()) // Update report list based on displayed filters
      }
    }
  }

  private fun setupSortFilterViews() {
    arguments?.getString(PreSelectedFilterExtra)?.let { filterName ->
      viewModel.selectedFilters = arrayListOf(FilterItem(filterName, true, "Precaution Type"))
    }

    selectedFilterAdapter = SelectedFilterAdapter { _, _, position -> // RemoveButton() - View, FilterItem, Int
      viewModel.selectedFilters.removeAt(position) // First remove filter from selectedFilterList
      selectedFilterAdapter.notifyItemRemoved(position) // Bit more efficient than submitting whole new list to diff
      reportsAdapter.submitList(viewModel.sortedFilteredList())
    }

    selectedFilterRV = viewBinding.selectedFilterRV.apply {
      adapter = selectedFilterAdapter.apply { submitList(viewModel.selectedFilters) }
      layoutManager = FlexboxLayoutManager(context, FlexDirection.ROW, FlexWrap.WRAP)
        .apply { justifyContent = JustifyContent.CENTER }
    }
  }
  private fun setupReportRV() {
    reportsRV = viewBinding.reportRV.apply {
      reportsAdapter = ReportAdapter().also { adapter = it } // ALSO setAdapter for reportsRV
      val tintedLine = ContextCompat.getDrawable(context, R.drawable.custom_item_divider) // Get line drawable
        ?.apply { setTint(ContextCompat.getColor(context, R.color.colorPrimaryDark)) } // Color the line
      addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply { setDrawable(tintedLine!!) })
    }

    viewModel.reportState.observe(viewLifecycleOwner) { (loading, newList) ->
      reportsAdapter.submitList(viewModel.sortedFilteredList(newList))
      with(sorryMsgTextView) {
        text = when {
          loading -> "Looking up reports"
          newList.isEmpty() -> "Weird! Seems we don't have any available reports to view!"
          else -> "Please try again later!"
        }
        visibility = if (newList.isEmpty()) View.VISIBLE else View.INVISIBLE
      }
    }
  }

  private inner class ReportListMenu: MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
      menuInflater.inflate(R.menu.report_list_actions, menu)
      setUpSearchView(menu)
    }
    override fun onMenuItemSelected(item: MenuItem): Boolean {
      return onNavDestinationSelected(item, findNavController()) // Replaces fragmentManager.popBackStack() & activity?.finish()
    }
    // SEARCHBAR in Toolbar - Called in toolbar createOptionsMenu
    private fun setUpSearchView(menu : Menu) {
      val searchMenu = menu.findItem(R.id.search_action)
      searchMenu.setOnActionExpandListener(ActionViewExpansionListener())

      (searchMenu?.actionView as EditText).apply {
        setHint(R.string.search_hint)
        setHintTextColor(ContextCompat.getColor(requireContext(), R.color.colorLight))

        maxLines = 1; inputType = TYPE_CLASS_TEXT // Need BOTH of these to restrict text to single line

        background.colorFilter = PorterDuffColorFilter(ContextCompat
          .getColor(requireContext(), R.color.colorLight), PorterDuff.Mode.SRC_IN)

      }.also { startSearchBarListener(it) }
    }
    @OptIn(FlowPreview::class)
    private fun startSearchBarListener(searchBar: EditText) {
      viewLifecycleOwner.lifecycleScope.launch { // This flow is bound to the lifecycle of the activity!
        searchBar.textUpdates().onEach { EspressoIdlingResource.increment() }
          .debounce(500) // Using flowWithLifecycle w/ its default minState STARTED is the best option!
          .flowWithLifecycle(lifecycle) // It'll guaranteed cancel collection onStop & no double firing like w/ RESUMED
          .collectLatest { newText -> // Flow version of Handler w/ postDelayed + cancelling last Runnable block!
            // collectLatest kills previous computation if a new value arrives!

            if (newText.isNullOrBlank()) { reportsAdapter.submitList(viewModel.sortedFilteredList()) }
            else { reportsAdapter.submitList(viewModel.textFilteredList(newText.toString())) }

            if (reportsAdapter.itemCount > 0) reportsRV.scrollToPosition(0) // Checking after update
            EspressoIdlingResource.decrement()
          }
      }
    }
  }
  // Needs access to fragment's setFragmentResult & requireActivity so must be inner and declared in same file
  private inner class ActionViewExpansionListener: MenuItem.OnActionExpandListener {
    private val focusListener = View.OnFocusChangeListener { view, isFocused ->
      if (!isFocused) { // When searchActionView loses focus, ask activity to hide keyboard + close actionView if no query
        setFragmentResult(KeyboardRequestKey, bundleOf(KeyboardIsClosingParcel to true))
        (view as? EditText)?.run {
          if (text.isEmpty()) setFragmentResult(ActionViewRequestKey, bundleOf(ActionViewIsClosingParcel to true))
        }
      }
    }
    override fun onMenuItemActionExpand(searchIcon: MenuItem): Boolean { // Returning true lets expansion/collapse to happen
      val searchBar = searchIcon.actionView as EditText
      searchBar.requestFocus()
      if (searchBar.onFocusChangeListener == null) searchBar.onFocusChangeListener = focusListener
      return true
    }
    override fun onMenuItemActionCollapse(searchIcon: MenuItem): Boolean {
      val searchBar = searchIcon.actionView as EditText
      if (requireActivity().currentFocus == searchBar && searchBar.text.isNotEmpty()) {
        searchBar.text.clear()
        setFragmentResult(KeyboardRequestKey, bundleOf(KeyboardIsClosingParcel to true)) // Hide keyboard
        searchBar.onFocusChangeListener = null // Prevents double collapse nullException call (listening restarts on next expansion)
        return true
      }
      else if (requireActivity().currentFocus == searchBar) {
        searchBar.clearFocus(); return false // Clear focus and let focusListener close actionView
      }
      // Made it here, either user didn't interact at all or above "else if" ran so clearFocus() let focusListener run collapse
      setFragmentResult(KeyboardRequestKey, bundleOf(KeyboardIsClosingParcel to true)) // Hide keyboard
      searchBar.text.clear(); return true
    }
  }
}