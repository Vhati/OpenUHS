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
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
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


public class HotSpotNodeView extends NodeView {

	private final Logger logger = LoggerFactory.getLogger( AndroidUHSConstants.LOG_TAG );

	protected int overlayOutlineColor = Color.rgb( 0x80, 0x80, 0x80 );  // Gray.
	protected int overlayZoneColor = Color.rgb( 0xFF, 0xA5, 0x00 );  // Orange.
	protected int linkColor = Color.rgb( 0x00, 0x80, 0x00 );  // Green.
	protected int otherColor = Color.rgb( 0x00, 0x00, 0xFF );  // Blue.

	protected UHSHotSpotNode hotspotNode = null;
	protected Bitmap mainBitmap = null;
	protected RectF originalMainRect = null;
	protected RectF currentMainRect = null;

	protected float minScale = 1f;
	protected float scale = 1f;
	protected Rect panMainRect = new Rect();
	protected RectF viewRect = new RectF();
	protected List<ZoneHolder> zoneHolders = new ArrayList<ZoneHolder>();

	protected Paint rectPaint;

	private GestureDetector gestureDetector;
	private ScaleGestureDetector scaleDetector;


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
			public boolean onSingleTapUp( MotionEvent e ) {
				if ( hotspotNode != null ) {
					for ( ZoneHolder zoneHolder : zoneHolders ) {
						float paddedX = e.getX() + HotSpotNodeView.this.getPaddingLeft();
						float paddedY = e.getY() + HotSpotNodeView.this.getPaddingTop();

						if ( zoneHolder.currentZoneRect.contains( paddedX, paddedY ) ) {

							if ( zoneHolder.imageBitmap != null ) {
								zoneHolder.revealed = !zoneHolder.revealed;
								HotSpotNodeView.this.invalidate();
								// Keep looping through layers? Sure.
							}
							else if ( zoneHolder.linkTarget != -1 ) {
								HotSpotNodeView.this.getNavCtrl().setReaderNode( zoneHolder.linkTarget );
								break;
							}
						}
					}
				}

				return true;
			}

			@Override
			public boolean onScroll( MotionEvent e1, MotionEvent e2, float distanceX, float distanceY ) {

				handleScroll( distanceX, distanceY );  // Delta since last scroll, NOT since initial drag.
				return true;
			}

			@Override
			public boolean onDown( MotionEvent e ) {
				return true;  // Necessary for all other gesture methods to work.
			}
		});

		scaleDetector = new ScaleGestureDetector( context, new SimpleOnScaleGestureListener() {
			public boolean onScale( ScaleGestureDetector detector ) {
				float newScale = scale;
				newScale *= detector.getScaleFactor();
				newScale = Math.max( minScale, Math.min( newScale, 2.0f ) );
				handleScale( newScale );

				return true;
			}
		});


		this.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch( View v, MotionEvent e ) {
				if ( gestureDetector.onTouchEvent( e ) ) {
					return true;
				}
				else if ( scaleDetector.onTouchEvent( e ) ) {
					return true;
				}
				// Check MotionEvent.ACTION_UP here if interested in end-of-scroll.

				return false;
			}
		});
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
			panMainRect.offsetTo( (int)currentMainRect.centerX() - panMainRect.centerX(), (int)currentMainRect.centerY() - panMainRect.centerY() );

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

						if ( showAll ) zoneHolder.revealed = true;
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
			resetScale();
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

		resetScale();
		this.invalidate();
	}

	/**
	 * Resets the zoom state, fitting and centering content.
	 *
	 * <p>This needs to be called when either this View's dimensions change or
	 * when a new node is set - if this View has non-zero dimensions.</p>
	 */
	protected void resetScale() {
		// Account for padding.
		float contentWidth = (float)this.getWidth() - (float)(this.getPaddingLeft() + this.getPaddingRight());
		float contentHeight = (float)this.getHeight() - (float)(this.getPaddingTop() + this.getPaddingBottom());

		viewRect.set( 0, 0, contentWidth, contentHeight );

		if ( hotspotNode != null ) {
			float fitX = contentWidth / originalMainRect.width();
			float fitY = contentHeight / originalMainRect.height();
			minScale = Math.min( fitX, fitY );
		}
		else {
			minScale = 1f;
		}

		handleScale( minScale );
	}

	/**
	 * Sets a new scaling factor for content.
	 *
	 * <p>This will resize/reposition the main image pan view and all zones.</p>
	 *
	 * <p>After resizing, the pan rectangle will be centered on the same spot.</p>
	 */
	private void handleScale( float newScale ) {
		if ( hotspotNode == null ) return;

		scale = newScale;

		//currentMainRect.set( originalMainRect );

		// Resize panMainRect, then shift back to maintain the center.
		int prevPanCenterX = panMainRect.centerX();
		int prevPanCenterY = panMainRect.centerY();
		panMainRect.set( 0, 0, (int)(viewRect.width() / scale), (int)(viewRect.height() / scale) );

		panMainRect.offsetTo( prevPanCenterX - panMainRect.centerX(), prevPanCenterY - panMainRect.centerY() );

		for ( ZoneHolder zoneHolder : zoneHolders ) {
			float newZoneL = zoneHolder.originalZoneRect.left * scale;
			float newZoneT = zoneHolder.originalZoneRect.top * scale;
			float newZoneR = zoneHolder.originalZoneRect.right * scale;
			float newZoneB = zoneHolder.originalZoneRect.bottom * scale;
			zoneHolder.currentZoneRect.set( newZoneL, newZoneT, newZoneR, newZoneB );
			zoneHolder.currentZoneRect.offset( -panMainRect.left * scale, -panMainRect.top * scale );

			if ( zoneHolder.imageRef != null ) {
				float newOverlayL = zoneHolder.originalOverlayRect.left * scale;
				float newOverlayT = zoneHolder.originalOverlayRect.top * scale;
				float newOverlayR = zoneHolder.originalOverlayRect.right * scale;
				float newOverlayB = zoneHolder.originalOverlayRect.bottom * scale;
				zoneHolder.currentOverlayRect.set( newOverlayL, newOverlayT, newOverlayR, newOverlayB );
				zoneHolder.currentOverlayRect.offset( -panMainRect.left * scale, -panMainRect.top * scale );
			}
		}

		handleScroll( 0, 0 );  // Bring pan rect toward center during zoom-out, and invalidate.
	}


	/**
	 * Incrementally nudges content.
	 *
	 * <p>If an axis of the pan view is larger than the main image, that
	 * axis will not scroll. (Presumably it will have been centered
	 * already.)</p>
	 */
	private void handleScroll( float distanceX, float distanceY ) {
		if ( hotspotNode == null ) return;

		float panOffsetX = distanceX / scale;  // TODO: Convert offset to scaled intra-main-image space.
		float panOffsetY = distanceY / scale;

		// Clamp to prevent scrolling beyond the main image.
		if ( panMainRect.width() < currentMainRect.width() ) {
			if ( panMainRect.right + panOffsetX > currentMainRect.right ) {
				panOffsetX = currentMainRect.right - panMainRect.right;
			}
			else if ( panMainRect.left + panOffsetX < 0 ) {
				panOffsetX = 0 - panMainRect.left;
			}
		}
		else {
			panOffsetX = 0;
		}

		if ( panMainRect.height() < currentMainRect.height() ) {
			if ( panMainRect.bottom + panOffsetY > currentMainRect.bottom ) {
				panOffsetY = currentMainRect.bottom - panMainRect.bottom;
			}
			else if ( panMainRect.top + panOffsetY < 0 ) {
				panOffsetY = 0 - panMainRect.top;
			}
		}
		else {
			panOffsetY = 0;
		}

		panMainRect.offset( (int)panOffsetX, (int)panOffsetY );

		float screenOffsetX = panOffsetX * scale;  // TODO: Convert back to unscaled screen coords.
		float screenOffsetY = panOffsetY * scale;

		for ( ZoneHolder zoneHolder : zoneHolders ) {
			float newZoneL = zoneHolder.originalZoneRect.left * scale;
			float newZoneT = zoneHolder.originalZoneRect.top * scale;
			zoneHolder.currentZoneRect.offsetTo( newZoneL, newZoneT );
			zoneHolder.currentZoneRect.offset( -panMainRect.left * scale, -panMainRect.top * scale );

			if ( zoneHolder.imageRef != null ) {
				float newOverlayL = zoneHolder.originalOverlayRect.left * scale;
				float newOverlayT = zoneHolder.originalOverlayRect.top * scale;

				zoneHolder.currentOverlayRect.offsetTo( newOverlayL, newOverlayT );
				zoneHolder.currentOverlayRect.offset( -panMainRect.left * scale, -panMainRect.top * scale );
			}
		}

		this.invalidate();
	}


	@Override
	protected void onDraw( Canvas canvas ) {
		super.onDraw( canvas );

		if ( mainBitmap != null ) {
			canvas.drawBitmap( mainBitmap, panMainRect, viewRect, null );
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
