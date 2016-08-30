package net.vhati.openuhs.androidreader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.content.IntentCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.androidreader.reader.NodeAdapter;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSParser;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.core.markup.DecoratedFragment;


public class ReaderActivity extends AppCompatActivity implements View.OnClickListener {
  public static final String EXTRA_OPEN_FILE = "net.openuhs.androidreader.OpenFile";

  public static final int SCROLL_TO_TOP = 0;
  public static final int SCROLL_TO_BOTTOM = 1;
  public static final int SCROLL_IF_INCOMPLETE = 2;

  private Toolbar toolbar = null;

  private String readerTitle = "";

  private UHSRootNode rootNode = null;
  private UHSNode currentNode = null;

  private NodeAdapter nodeAdapter = null;
  private ListView listView = null;

  private List<UHSNode> historyArray = new ArrayList<UHSNode>();
  private List<UHSNode> futureArray = new ArrayList<UHSNode>();
  private boolean showAllOverride = false;

  private TextView questionLbl = null;
  private ImageButton backBtn = null;
  private ImageButton forwardBtn = null;
  private TextView shownHintsCountLbl = null;
  private ImageButton showNextBtn = null;

  private ReaderActivity navCtrl = this;
  private UHSNode dummyNode = new UHSNode("Blank");


  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    this.setContentView(R.layout.reader);

    toolbar = (Toolbar)findViewById(R.id.readerToolbar);
    toolbar.setTitle("");  // Ensure it's non-null so support will defer its text to Toolbar.
    this.setSupportActionBar(toolbar);

    questionLbl = (TextView)findViewById(R.id.questionLbl);
    backBtn = (ImageButton)findViewById(R.id.backBtn);
    forwardBtn = (ImageButton)findViewById(R.id.forwardBtn);
    shownHintsCountLbl = (TextView)findViewById(R.id.shownHintsCountLbl);
    showNextBtn = (ImageButton)findViewById(R.id.showNextBtn);

    backBtn.setEnabled(false);
    forwardBtn.setEnabled(false);

    listView = (ListView)findViewById(R.id.childNodesList);
    nodeAdapter = new NodeAdapter(this, dummyNode, true);
    listView.setAdapter(nodeAdapter);

    backBtn.setOnClickListener(this);
    forwardBtn.setOnClickListener(this);
    showNextBtn.setOnClickListener(this);

    listView.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        //Log.i("OpenUHS", "@!@ Clicked list item #"+ position);
        if (parent != listView) return;

        Object o = nodeAdapter.getItem(position);
        if ((o instanceof UHSNode) == false) return;

        UHSNode childNode = (UHSNode)o;
        if (childNode.isGroup()) {
          navCtrl.setReaderNode(childNode);
        }
        else if (childNode.isLink()) {
          int targetIndex = childNode.getLinkTarget();
          navCtrl.setReaderNode(targetIndex);
        }
      }
    });
    // When list items are focusable or clickable, they preempt the list's click listener.
    // This is a workaround reminder, in case it's ever needed.
    //   android:descendantFocusability="blocksDescendants"
    //   listView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

    // Check bundle args for a file to open.
    String uhsPath = null;
    if (savedInstanceState == null) {
      Bundle extras = this.getIntent().getExtras();
      if (extras == null) {
        uhsPath = null;
      } else {
        uhsPath = extras.getString(EXTRA_OPEN_FILE);
      }
    }
    else {
      uhsPath = (String)savedInstanceState.getSerializable(EXTRA_OPEN_FILE);
    }

    if (uhsPath != null) {
      UHSRootNode inRootNode = null;
      try {
        File uhsFile = new File(uhsPath);
        UHSParser uhsParser = new UHSParser();
        inRootNode = uhsParser.parseFile(uhsFile, UHSParser.AUX_NEST);
      }
      catch (Exception e) {
        // TODO: Report the error properly.
        TextView tv = new TextView(this);
        tv.setText(e.toString());
        this.setContentView(tv);
      }

      if (inRootNode != null) {
        reset();
        setUHSNodes(inRootNode, inRootNode);
      }
    }

    /*
    UHSParser uhsParser = new UHSParser();
    String tmp = uhsParser.decryptString("4w{ Bw d srJ zH JDr yJptIy");
    TextView tv = new TextView(this);
    tv.setText(tmp);
    this.setContentView(tv);
    */
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.reader_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.showAllHintsAction:
        showAllOverride = !item.isChecked();
        item.setChecked(showAllOverride);

        if (showAllOverride) {  // Reveal all hints here if it is now checked.
          while (showNext() != false);
        }
        return true;

      case R.id.switchToDownloaderAction:
        Intent intent = new Intent().setClass(this, DownloaderActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK | IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME);
        this.startActivity(intent);
        finish();
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }


  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.backBtn:
        setReaderNode(historyArray.get(historyArray.size()-1));
        break;

      case R.id.forwardBtn:
        setReaderNode(futureArray.get(futureArray.size()-1));
        break;

      case R.id.showNextBtn:
        showNext();
        scrollTo(SCROLL_TO_BOTTOM);
        break;
    }
  }


  /**
   * Clears everything.
   */
  public void reset() {
    historyArray.clear();
    futureArray.clear();
    backBtn.setEnabled(false);
    forwardBtn.setEnabled(false);
    //findBtn.setEnabled(false);

    shownHintsCountLbl.setText("");
    showNextBtn.setEnabled(false);

    rootNode = null;
    currentNode = null;

    nodeAdapter.setNode(dummyNode, true);
    nodeAdapter.notifyDataSetChanged();

    setReaderTitle(null);
  }


  /**
   * Displays a new UHS tree.
   *
   * @param inCurrentNode the new initial node
   * @param inRootNode the new root node
   */
  public void setUHSNodes(UHSNode inCurrentNode, UHSRootNode inRootNode) {
    reset();
    rootNode = inRootNode;
    //findBtn.setEnabled(true);
    setReaderNode(inCurrentNode);
  }


  /**
   * Displays a new node within the current tree.
   * <br />If the node is the same as the next/prev one, breadcrumbs will be traversed.
   *
   * @param newNode the new node
   */
  public void setReaderNode(UHSNode newNode) {
    if (newNode == null) {return;}

    int matchesNextPrev = 0; // -1 prev, 1 next, 0 neither.

    if (historyArray.size() > 0 && historyArray.get(historyArray.size()-1).equals(newNode)) {
      matchesNextPrev = -1;
    }
    else if (futureArray.size() > 0 && futureArray.get(futureArray.size()-1).equals(newNode)) {
      matchesNextPrev = 1;
    }


    if (matchesNextPrev == -1) {
      // Move one node into the past.
      historyArray.remove(historyArray.size()-1);
      if (currentNode != null) {
        // Leave a breadcrumb before changing to the new node.
        futureArray.add(currentNode);
      }
    }
    else if (matchesNextPrev == 1) {
      // Move one node into the future.
      futureArray.remove(futureArray.size()-1);
      if (currentNode != null) {
        // Leave a breadcrumb before changing to the new node.
        historyArray.add(currentNode);
      }
    }
    else {
      if (currentNode != null) {
        // Leave a breadcrumb before changing to the new node.
        historyArray.add(currentNode);
      }
      // Wipe the future.
      futureArray.clear();
    }
    backBtn.setEnabled( (historyArray.size() > 0) );
    forwardBtn.setEnabled( (futureArray.size() > 0) );


    currentNode = newNode;
    boolean needToShowAll = false;

    if (currentNode == rootNode) {
      questionLbl.setText("");
      setReaderTitle((String)currentNode.getContent());
      needToShowAll = true;
    }
    else {
      if (currentNode.getContentType() == UHSNode.STRING) {
        StringBuffer questionBuf = new StringBuffer();
        questionBuf.append(currentNode.getType()).append("=");

        if (currentNode.getStringContentDecorator() != null) {
          DecoratedFragment[] fragments = currentNode.getDecoratedStringContent();
          for (int i=0; i < fragments.length; i++) {
            questionBuf.append(fragments[i].fragment);
          }
        }
        else {
          questionBuf.append((String)currentNode.getContent());
        }
        questionLbl.setText(questionBuf.toString());
      }
      else {
        questionLbl.setText("");
      }
    }
    needToShowAll = (needToShowAll || showAllOverride);
    nodeAdapter.setNode(newNode, needToShowAll);

    scrollTo(SCROLL_IF_INCOMPLETE);

    boolean complete = nodeAdapter.isComplete();
    shownHintsCountLbl.setText(String.format("%d/%d", (complete ? currentNode.getChildCount() : currentNode.getRevealedAmount()), currentNode.getChildCount()));
    showNextBtn.setEnabled(!complete);

    nodeAdapter.notifyDataSetChanged();
  }


  /**
   * Displays a new node within the current tree.
   * <br />Nothing will happen if the ID isn't among the root node's list of link targets.
   *
   * @param id ID of the new node
   */
  public void setReaderNode(int id) {
    UHSNode tmpNode = rootNode.getLink(id);
    if (tmpNode != null) {
      setReaderNode(tmpNode);
    } else {
      Toast.makeText(this, "Could not find link target: "+ id, Toast.LENGTH_SHORT).show();
    }
  }


  /**
   * Sets the reader's title to the specified string.
   *
   * @param s a title (null is treated as "")
   */
  public void setReaderTitle(String s) {
    readerTitle = ((s !=null) ? s : "");
    toolbar.setTitle(readerTitle);
  }

  /**
   * Gets the title of the reader.
   *
   * @return the title of the reader
   */
  public String getReaderTitle() {
    return readerTitle;
  }


  /**
   * Reveals the next hint of the current node panel.
   *
   * @return true if successful, false otherwise
   */
  public boolean showNext() {
    if (nodeAdapter == null) return false;

    int revealedIndex = nodeAdapter.showNext();
    nodeAdapter.notifyDataSetChanged();

    if (revealedIndex == -1) {
      showNextBtn.setEnabled(false);
      return false;
    } else {
      int hintCount = currentNode.getChildCount();
      if ((revealedIndex+1) == hintCount) showNextBtn.setEnabled(false);
      shownHintsCountLbl.setText(String.format("%d/%d", (revealedIndex+1), hintCount));
      return true;
    }
  }


  /**
   * Scrolls to the top/bottom of the visible hints.
   *
   * @param position either SCROLL_TO_TOP, SCROLL_TO_BOTTOM, or SCROLL_IF_INCOMPLETE
   */
  public void scrollTo(int position) {
    if (position == SCROLL_IF_INCOMPLETE) {
      if (nodeAdapter.isComplete()) position = SCROLL_TO_TOP;
      else position = SCROLL_TO_BOTTOM;
    }
    if (position == SCROLL_TO_TOP) {
      listView.smoothScrollToPosition(0);
    }
    else if (position == SCROLL_TO_BOTTOM) {
      listView.smoothScrollToPosition(nodeAdapter.getCount()-1);
    }
  }
}
