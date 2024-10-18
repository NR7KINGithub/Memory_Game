package com.example.memory

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.memory.models.BoardSize
import com.example.memory.models.MemoryGame
import com.example.memory.models.UserImageList
import com.example.memory.utils.EXTRA_BOARD_SIZE
import com.example.memory.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 248
    }

    private lateinit var clRoot: CoordinatorLayout
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView

    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    private var boardSize: BoardSize = BoardSize.EASY

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

        setUpBoard()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog("Quit Your Current Game?", null, View.OnClickListener {
                        setUpBoard()
                    })
                } else {
                    setUpBoard()
                }
            }
            R.id.mi_new_size -> {
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
                return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null) {
                Log.e(TAG, "Got Null Custom Game From CreateActivity!")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @SuppressLint("InflateParams")
    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog("Fetch Memory Game", boardDownloadView, View.OnClickListener {
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid Custom Game Data From Firestore!")
                Snackbar.make(clRoot, "Sorry! Game '$gameName' Does Not Exist", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, "You're Now Playing '$customGameName'!", Snackbar.LENGTH_LONG).show()
            gameName = customGameName
            setUpBoard()
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception When Retrieving Game!", exception)
        }
    }

    @SuppressLint("InflateParams")
    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog("Create Your Own Memory Board", boardSizeView, View.OnClickListener {
            val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE)
        })
    }

    @SuppressLint("InflateParams")
    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }
        showAlertDialog("Choose New Size", boardSizeView, View.OnClickListener {
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setUpBoard()
        })
    }

    private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                positiveClickListener.onClick(null)
            }.show()
    }

    @SuppressLint("SetTextI18n")
    private fun setUpBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMoves.text = "Easy: 4x2"
                tvNumPairs.text = "Pairs: 0/4"
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text = "Medium: 6x3"
                tvNumPairs.text = "Pairs: 0/9"
            }
            BoardSize.HARD -> {
                tvNumMoves.text = "Hard: 7x4"
                tvNumPairs.text = "Pairs: 0/14"
            }
        }

        // Apply gradient to TextViews
        applyGradientToTextView(tvNumMoves)
        applyGradientToTextView(tvNumPairs)

        memoryGame = MemoryGame(boardSize, customGameImages)
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object : MemoryBoardAdapter.CardClickListener {
            override fun onCardClicked(position: Int) {
                updateGameWithFlip(position)
            }
        })
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    @SuppressLint("SetTextI18n", "NotifyDataSetChanged")
    private fun updateGameWithFlip(position: Int) {
        // Error Checking
        if (memoryGame.haveWonGame()) {
            Snackbar.make(clRoot, "You Already Won!", Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.isCardFaceUp(position)) {
            Snackbar.make(clRoot, "Invalid Move!", Snackbar.LENGTH_SHORT).show()
            return
        }
        // Actually flip over the card
        if (memoryGame.flipCard(position)) {
            Log.i(TAG, "Found A Match! Num Pairs Found: ${memoryGame.numPairsFound}")
            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound}/${boardSize.getNumPairs()}"

            // Apply gradient after updating text
            applyGradientToTextView(tvNumPairs)

            if (memoryGame.haveWonGame()) {
                Snackbar.make(clRoot, "You Won! Congratulations", Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
            }
        }
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        applyGradientToTextView(tvNumMoves)  // Apply gradient after updating text
        adapter.notifyDataSetChanged()
    }

    private fun applyGradientToTextView(textView: TextView) {
        val width = textView.paint.measureText(textView.text.toString())
        val textShader = LinearGradient(0f, 0f, width, textView.textSize,
            intArrayOf(
                Color.parseColor("#f2461b"),
                Color.parseColor("#f4b63d")
            ),
            null,
            Shader.TileMode.CLAMP)
        textView.paint.shader = textShader
    }
}