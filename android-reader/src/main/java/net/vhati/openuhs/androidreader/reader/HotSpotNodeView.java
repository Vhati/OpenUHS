package net.vhati.openuhs.androidreader.reader;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.androidreader.AndroidUHSConstants;
import net.vhati.openuhs.androidreader.reader.NodeView;
import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;


public class HotSpotNodeView extends NodeView implements View.OnTouchListener {

	private final Logger logger = LoggerFactory.getLogger( AndroidUHSConstants.LOG_TAG );

	protected int overlayOutlineColor = Color.rgb( 0x80, 0x80, 0x80 );  // Gray.
	protected int overlayZoneColor = Color.rgb( 0xFF, 0xA5, 0x00 );  // Orange.
	protected int linkColor = Color.rgb( 0x00, 0x80, 0x00 );  // Green.
	protected int otherColor = Color.rgb( 0x00, 0x00, 0xFF );  // Blue.

	protected UHSHotSpotNode hotspotNode = null;
	protected Bitmap mainBitmap = null;
	protected RectF originalMainRect = null;
	protected RectF currentMainRect = null;
	protected List<ZoneHolder> zoneHolders = new ArrayList<ZoneHolder>();

	protected Paint rectPaint;
	protected GestureDetector gestureDetector;


	public HotSpotNodeView( Context context ) {
		super( context );
		this.setClickable( true );
		this.setFocusable( false );
		this.setWillNotDraw( false );  // Layouts have onDraw() disabled by default.

		rectPaint = new Paint( Paint.ANTI_ALIAS_FLAG );
		rectPaint.setColor( otherColor );
		rectPaint.setStyle( Paint.Style.STROKE );
		rectPaint.setStrokeWidth( 1 );
		rectPaint.setStrokeCap( Paint.Cap.ROUND );
		rectPaint.setStrokeJoin( Paint.Join.ROUND );
		rectPaint.setStrokeMiter( 1 );
		rectPaint.setPathEffect( new DashPathEffect( new float[] {1f, 2f}, 0 ) );

		gestureDetector = new GestureDetector( context, new SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapUp( MotionEvent event ) {
				return true;
			}
		});

		this.setOnTouchListener( this );
	}

	@Override
	public boolean accept( UHSNode node ) {
		if ( node instanceof UHSHotSpotNode ) return true;
		return false;
	}

	@Override
	public void setNode( UHSNode node, boolean showAll ) {
		reset();
		if ( !accept( node ) ) return;

		super.setNode( node, showAll );
		hotspotNode = (UHSHotSpotNode)node;

		InputStream is = null;
		try {
			ByteReference mainImageRef = hotspotNode.getRawImageContent();

			BitmapFactory.Options opts = new BitmapFactory.Options();
			is = mainImageRef.getInputStream();
			mainBitmap = BitmapFactory.decodeStream( is, null, opts );
			if ( mainBitmap == null ) throw new IOException( "Failed to read main bitmap" );
			is.close();
			originalMainRect = new RectF( 0, 0, mainBitmap.getWidth(), mainBitmap.getHeight() );
			currentMainRect = new RectF( originalMainRect );

			for ( int i=0; i < hotspotNode.getChildCount(); i++ ) {
				UHSNode childNode = hotspotNode.getChild( i );
				String childType = childNode.getType();

				HotSpot spot = hotspotNode.getSpot( childNode );

				ZoneHolder zoneHolder = new ZoneHolder();
				zoneHolder.originalZoneRect = new RectF( spot.zoneX, spot.zoneY, spot.zoneX+spot.zoneW, spot.zoneY+spot.zoneH );
				zoneHolder.currentZoneRect = new RectF( zoneHolder.originalZoneRect );
				zoneHolder.title = childNode.getDecoratedStringContent();

				if ( "Overlay".equals( childType ) && childNode instanceof UHSImageNode ) {
					UHSImageNode overlayNode = (UHSImageNode)childNode;

					zoneHolder.imageRef = overlayNode.getRawImageContent();
					if ( zoneHolder.imageRef != null ) {

						opts = new BitmapFactory.Options();
						is = zoneHolder.imageRef.getInputStream();
						Bitmap overlayBitmap = BitmapFactory.decodeStream( is, null, opts );
						if ( overlayBitmap == null ) throw new IOException( "Failed to read overlay bitmap" );
						is.close();

						zoneHolder.imageBitmap = overlayBitmap;
						zoneHolder.originalOverlayRect = new RectF( 0, 0, overlayBitmap.getWidth(), overlayBitmap.getHeight() );
						zoneHolder.originalOverlayRect.offsetTo( spot.x, spot.y );
						zoneHolder.currentOverlayRect = new RectF( zoneHolder.originalOverlayRect );
					}
				}
				else {
					zoneHolder.linkTarget = childNode.getLinkTarget();
				}

				zoneHolders.add( zoneHolder );
			}

		}
		catch ( IOException e ) {
			logger.error( "Error loading image: {}", e );
			Toast.makeText( this.getContext(), "Error loading image", Toast.LENGTH_LONG ).show();

			reset();
			return;
		}
		finally {
			try {if ( is != null ) is.close();} catch ( IOException e ) {}
		}

		if ( this.getWidth() > 0 && this.getHeight() > 0 ) {
			// After reusing this view, the next node triggers onMeasure().
			// But onSizeChanged() only happens if the measured size is
			// different.
			adjustContentToScale();
		}

		this.requestLayout();
		this.invalidate();
	}

	@Override
	public void reset() {
		if ( mainBitmap != null ) {
			mainBitmap.recycle();
			mainBitmap = null;
		}
		originalMainRect = null;
		currentMainRect = null;

		for ( ZoneHolder zoneHolder : zoneHolders ) {
			if ( zoneHolder.imageBitmap != null ) {
				zoneHolder.imageBitmap.recycle();
			}
		}
		zoneHolders.clear();

		hotspotNode = null;
		this.removeAllViews();
		super.reset();
		this.invalidate();
	}


	@Override
	public boolean onTouch( View v, MotionEvent e ) {
		if ( hotspotNode != null ) {
			// Check for "e.getAction() == MotionEvent.ACTION_UP", but
			// acounting for swipes, multiple taps, etc.

			if ( gestureDetector.onTouchEvent( e ) ) {
				for ( ZoneHolder zoneHolder : zoneHolders ) {
					float paddedX = e.getX() + this.getPaddingLeft();
					float paddedY = e.getY() + this.getPaddingTop();

					if ( zoneHolder.currentZoneRect.contains( paddedX, paddedY ) ) {

						if ( zoneHolder.imageBitmap != null ) {
							zoneHolder.revealed = !zoneHolder.revealed;
							HotSpotNodeView.this.invalidate();
						}
						else if ( zoneHolder.linkTarget != -1 ) {
							HotSpotNodeView.this.getNavCtrl().setReaderNode( zoneHolder.linkTarget );
						}
					}
				}
			}
		}
		return false;  // Don't consume the event.
	}


	@Override
	protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec ) {
		super.onMeasure( widthMeasureSpec, heightMeasureSpec );

		int widthSpecMode = MeasureSpec.getMode( widthMeasureSpec );
		int heightSpecMode = MeasureSpec.getMode( heightMeasureSpec );
		boolean resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
		boolean resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
		int minWidth = 0;
		int minHeight = 0;

		if ( resizeWidth || resizeHeight ) {  // Skip calc if not needed.
			if ( hotspotNode != null ) {
				minWidth = (int)originalMainRect.width() + this.getPaddingLeft() + this.getPaddingRight();
				minHeight = (int)originalMainRect.height() + this.getPaddingTop() + this.getPaddingBottom();
			}
		}

		int resolvedWidth = this.resolveSize( minWidth, widthMeasureSpec );
		int resolvedHeight = this.resolveSize( minHeight, heightMeasureSpec );
		this.setMeasuredDimension( resolvedWidth, resolvedHeight );
	}

	@Override
	protected void onSizeChanged( int w, int h, int oldw, int oldh ) {
		super.onSizeChanged( w, h, oldw, oldh );

		adjustContentToScale();
		this.invalidate();
	}

	protected void adjustContentToScale() {
		// Account for padding.
		float contentWidth = (float)this.getWidth() - (float)(this.getPaddingLeft() + this.getPaddingRight());
		float contentHeight = (float)this.getHeight() - (float)(this.getPaddingTop() + this.getPaddingBottom());

		if ( hotspotNode != null ) {
			float scaleX = contentWidth / originalMainRect.width();
			float scaleY = contentHeight / originalMainRect.height();
			float scale = Math.min( scaleX, scaleY );

			float newMainL = originalMainRect.left * scale;
			float newMainT = originalMainRect.top * scale;
			float newMainR = originalMainRect.right * scale;
			float newMainB = originalMainRect.bottom * scale;
			currentMainRect.set( newMainL, newMainT, newMainR, newMainB );

			for ( ZoneHolder zoneHolder : zoneHolders ) {
				float newZoneL = zoneHolder.originalZoneRect.left * scale;
				float newZoneT = zoneHolder.originalZoneRect.top * scale;
				float newZoneR = zoneHolder.originalZoneRect.right * scale;
				float newZoneB = zoneHolder.originalZoneRect.bottom * scale;
				zoneHolder.currentZoneRect.set( newZoneL, newZoneT, newZoneR, newZoneB );

				if ( zoneHolder.imageRef != null ) {
					float newOverlayL = zoneHolder.originalOverlayRect.left * scale;
					float newOverlayT = zoneHolder.originalOverlayRect.top * scale;
					float newOverlayR = zoneHolder.originalOverlayRect.right * scale;
					float newOverlayB = zoneHolder.originalOverlayRect.bottom * scale;
					zoneHolder.currentOverlayRect.set( newOverlayL, newOverlayT, newOverlayR, newOverlayB );
				}
			}
		}
	}


	@Override
	protected void onDraw( Canvas canvas ) {
		super.onDraw( canvas );

		if ( mainBitmap != null ) {
			canvas.drawBitmap( mainBitmap, null, currentMainRect, null );
		}

		int zoneHoldersCount = zoneHolders.size();
		for ( int i=0; i < zoneHoldersCount; i++ ) {
			ZoneHolder zoneHolder = zoneHolders.get( i );

			if ( zoneHolder.imageBitmap != null ) {
				if ( zoneHolder.revealed ) {
					canvas.drawBitmap( zoneHolder.imageBitmap, null, zoneHolder.currentOverlayRect, null );
				}
				else {
					rectPaint.setColor( overlayOutlineColor );
					canvas.drawRect( zoneHolder.currentOverlayRect, rectPaint );

					rectPaint.setColor( overlayZoneColor );
					canvas.drawRect( zoneHolder.currentZoneRect, rectPaint );
				}
			}
			else if ( zoneHolder.linkTarget != -1 ) {
				rectPaint.setColor( linkColor );
				canvas.drawRect( zoneHolder.currentZoneRect, rectPaint );
			}
			else {
				rectPaint.setColor( otherColor );
				canvas.drawRect( zoneHolder.currentZoneRect, rectPaint );
			}
		}

	}



	private static class ZoneHolder {
		public RectF originalZoneRect = null;
		public RectF originalOverlayRect = null;
		public String title = null;
		public ByteReference imageRef;
		public int linkTarget = -1;

		public Bitmap imageBitmap = null;

		public RectF currentZoneRect = null;
		public RectF currentOverlayRect = null;

		public boolean revealed = false;
	}
}
