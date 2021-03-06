package com.oganbelema.popularmovies.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.Group;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.oganbelema.network.model.movie.Movie;
import com.oganbelema.popularmovies.PopularMoviesApp;
import com.oganbelema.popularmovies.databinding.ActivityPopularMoviesListBinding;
import com.oganbelema.popularmovies.movie.FilterOptions;
import com.oganbelema.popularmovies.movie.MovieAdapter;
import com.oganbelema.popularmovies.movie.MovieItemOnClickListener;
import com.oganbelema.popularmovies.movie.PagedMovieAdapter;
import com.oganbelema.popularmovies.movie.viewmodel.MovieViewModelFactory;
import com.oganbelema.popularmovies.R;
import com.oganbelema.popularmovies.movie.viewmodel.MovieViewModel;

import java.util.List;

import javax.inject.Inject;

public class MovieListActivity extends AppCompatActivity implements MovieItemOnClickListener {

    private final String TAG = MovieListActivity.class.getSimpleName();

    private static final int GRID_SPAN_PORTRAIT = 2;

    private static final int GRID_SPAN_HORIZONTAL = 3;

    @Inject
    public MovieViewModelFactory mMovieViewModelFactory;

    private MovieViewModel mMovieViewModel;

    Group mLoadingIndicatorViews;

    Group mErrorViews;

    RecyclerView mMoviesRecyclerView;

    TextView mNoMoviesTextView;

    private MovieAdapter mMovieAdapter;

    private PagedMovieAdapter mPagedMovieAdapter;

    private GridLayoutManager mGridLayoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityPopularMoviesListBinding mActivityPopularMoviesListBinding = DataBindingUtil
                .setContentView(this, R.layout.activity_popular_movies_list);

        mLoadingIndicatorViews = mActivityPopularMoviesListBinding.loaderViews;

        mErrorViews = mActivityPopularMoviesListBinding.errorViews;

        mMoviesRecyclerView = mActivityPopularMoviesListBinding.moviesRecyclerView;

        mNoMoviesTextView = mActivityPopularMoviesListBinding.noMoviesTextView;

        ((PopularMoviesApp) getApplication()).getAppComponent().inject(this);

        mMovieViewModel = ViewModelProviders.of(this, mMovieViewModelFactory)
                .get(MovieViewModel.class);

        mMovieAdapter = mMovieViewModel.getMovieAdapter();

        mPagedMovieAdapter = mMovieViewModel.getPagedMovieAdapter();

        mMovieAdapter.setMovieItemOnClickListener(this);

        mPagedMovieAdapter.setMovieItemOnClickListener(this);

        mGridLayoutManager = new GridLayoutManager(this, GRID_SPAN_PORTRAIT);

        mMoviesRecyclerView.setLayoutManager(mGridLayoutManager);

        mMovieViewModel.getFilterOptions().observe(this, this::handleFilterOption);

        mMovieViewModel.getError().observe(this, error -> {
            if (error != null){
                showErrorView();
                Log.e(TAG, error.getLocalizedMessage(), error);
                Snackbar.make(mMoviesRecyclerView, error.getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        });

        mActivityPopularMoviesListBinding.errorTextView.setOnClickListener(view -> {
            FilterOptions option = mMovieViewModel.getRawFilterOption();

            if (option == null){
                option = FilterOptions.POPULAR_MOVIES;
            }

            handleFilterOption(option);
        });

        mMovieViewModel.getNetworkStatus().observe(this, networkStatus -> {
            if (!networkStatus){
                mMovieViewModel.setFilterOption(FilterOptions.FAVORITE_MOVIES);
                filterToFavoriteMovies();
                Snackbar.make(mMoviesRecyclerView, getString(R.string.offline_message),
                        Snackbar.LENGTH_LONG).show();
            }
        });

        mActivityPopularMoviesListBinding.swipeRefresh.setOnRefreshListener(() -> {
            if (mMovieViewModel.getRawFilterOption() != null){
                if (mMovieViewModel.getRawFilterOption().equals(FilterOptions.POPULAR_MOVIES)){
                    mMovieViewModel.resetPopularMovies();
                } else if (mMovieViewModel.getRawFilterOption().equals(FilterOptions.FAVORITE_MOVIES)){
                    mMovieViewModel.resetTopRatedMovies();
                }
            }

            mActivityPopularMoviesListBinding.swipeRefresh.setRefreshing(false);
        });

    }

    private void handleFilterOption(FilterOptions filterOptions) {
        switch (filterOptions) {
            case POPULAR_MOVIES:
                filterToPopularMovies();
                break;
            case TOP_RATED_MOVIES:
                filterToTopRatedMovies();
                break;
            case FAVORITE_MOVIES:
                filterToFavoriteMovies();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mGridLayoutManager.setSpanCount(GRID_SPAN_HORIZONTAL);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mGridLayoutManager.setSpanCount(GRID_SPAN_PORTRAIT);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.movie_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_filter:
                if (mMovieViewModel.getFilterOptions().getValue() == FilterOptions.POPULAR_MOVIES) {
                    mMovieViewModel.setFilterOption(FilterOptions.TOP_RATED_MOVIES);
                } else {
                    mMovieViewModel.setFilterOption(FilterOptions.POPULAR_MOVIES);
                }
                return true;

            case R.id.action_filter_favorite:
                mMovieViewModel.setFilterOption(FilterOptions.FAVORITE_MOVIES);
                return true;

        }

        return super.onOptionsItemSelected(item);
    }

    private void filterToTopRatedMovies() {
        setTitle(R.string.top_rated_movies);
        mMoviesRecyclerView.setAdapter(mPagedMovieAdapter);
        getTopRatedMovies();
    }

    private void filterToPopularMovies() {
        setTitle(R.string.popular_movies);
        mMoviesRecyclerView.setAdapter(mPagedMovieAdapter);
        getPopularMovies();
    }

    private void filterToFavoriteMovies() {
        setTitle(R.string.favorite_movies);
        mMoviesRecyclerView.setAdapter(mMovieAdapter);
        getFavoriteMovies();
    }

    private void getPopularMovies() {
        showLoaderView();

        mMovieViewModel.getPopularMovieLiveData().observe(this, popularMovies -> {
                    if (popularMovies != null) {
                        displayPagedMovies(popularMovies);
                    }
                });
    }

    private void getTopRatedMovies() {
        showLoaderView();

        mMovieViewModel.getTopRatedMovieLiveData().observe(this, topRatedMovies -> {
            if (topRatedMovies != null) {
                displayPagedMovies(topRatedMovies);
            }
        });
    }

    private void getFavoriteMovies(){
        showLoaderView();

        mMovieViewModel.getFavoriteMovies().observe(this, movies -> {
            if (mMovieViewModel.getRawFilterOption().equals(FilterOptions.FAVORITE_MOVIES)){
                displayMovies(movies);
            }
        });
    }

    private void displayMovies(List<Movie> movies) {
        if (movies != null && !movies.isEmpty()){
            showMoviesView();
            mMovieAdapter.submitList(movies);
        } else {
            showNoMoviesView();
        }
    }

    private void displayPagedMovies(PagedList<Movie> movies){
        if (movies != null){
            showMoviesView();
            mPagedMovieAdapter.submitList(movies);
        } else {
            showNoMoviesView();
        }
    }

    private void showLoaderView() {
        mMoviesRecyclerView.setVisibility(View.GONE);
        mErrorViews.setVisibility(View.GONE);
        mNoMoviesTextView.setVisibility(View.GONE);
        mLoadingIndicatorViews.setVisibility(View.VISIBLE);
    }

    private void showMoviesView() {
        mErrorViews.setVisibility(View.GONE);
        mLoadingIndicatorViews.setVisibility(View.GONE);
        mNoMoviesTextView.setVisibility(View.GONE);
        mMoviesRecyclerView.setVisibility(View.VISIBLE);
    }

    private void showErrorView(){
        mMoviesRecyclerView.setVisibility(View.GONE);
        mLoadingIndicatorViews.setVisibility(View.GONE);
        mNoMoviesTextView.setVisibility(View.GONE);
        mErrorViews.setVisibility(View.VISIBLE);

    }

    private void showNoMoviesView(){
        mMoviesRecyclerView.setVisibility(View.GONE);
        mLoadingIndicatorViews.setVisibility(View.GONE);
        mErrorViews.setVisibility(View.GONE);
        mNoMoviesTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onMovieItemClicked(Movie movie) {
        Intent startMovieDetailActivityIntent =
                new Intent(this, MovieDetailActivity.class);
        startMovieDetailActivityIntent.putExtra(MovieDetailActivity.MOVIE, movie);
        startActivity(startMovieDetailActivityIntent);

    }

}
