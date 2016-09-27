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
	protected Bitmap mainImageBitmap = null;
	protected RectF fullSizeRect = null;
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

		try {
			ByteReference mainImageRef = hotspotNode.getRawImageContent();
			fullSizeRect = getOriginalImageSize( mainImageRef );
		}
		catch ( IOException e ) {
			logger.error( "Error loading image: {}", e );
			Toast.makeText( this.getContext(), "Error loading image", Toast.LENGTH_LONG ).show();

			reset();
			return;
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
		if ( mainImageBitmap != null ) {
			mainImageBitmap.recycle();
			mainImageBitmap = null;
		}
		fullSizeRect = null;

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

					if ( zoneHolder.scaledZoneRect.contains( paddedX, paddedY ) ) {

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
				minWidth = (int)fullSizeRect.width() + this.getPaddingLeft() + this.getPaddingRight();
				minHeight = (int)fullSizeRect.height() + this.getPaddingTop() + this.getPaddingBottom();
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
	}

	protected void adjustContentToScale() {
		// Account for padding.
		float contentWidth = (float)this.getWidth() - (float)(this.getPaddingLeft() + this.getPaddingRight());
		float contentHeight = (float)this.getHeight() - (float)(this.getPaddingTop() + this.getPaddingBottom());

		if ( hotspotNode != null ) {
			if ( mainImageBitmap != null ) {
				mainImageBitmap.recycle();
				mainImageBitmap = null;
			}
			for ( ZoneHolder zoneHolder : zoneHolders ) {
				if ( zoneHolder.imageBitmap != null ) {
					zoneHolder.imageBitmap.recycle();
					zoneHolder.imageBitmap = null;
				}
			}

			if ( zoneHolders.isEmpty() ) {  // Populate the list.
				for ( int i=0; i < hotspotNode.getChildCount(); i++ ) {
					UHSNode childNode = hotspotNode.getChild( i );
					String childType = childNode.getType();

					ZoneHolder zoneHolder = new ZoneHolder();
					zoneHolder.originalSpot = hotspotNode.getSpot( childNode );
					zoneHolder.title = childNode.getDecoratedStringContent();

					if ( "Overlay".equals( childType ) && childNode instanceof UHSImageNode ) {
						UHSImageNode overlayNode = (UHSImageNode)childNode;

						zoneHolder.imageRef = overlayNode.getRawImageContent();
					}
					else {
						zoneHolder.linkTarget = childNode.getLinkTarget();
					}

					zoneHolders.add( zoneHolder );
				}
			}

			try {
				ByteReference mainImageRef = hotspotNode.getRawImageContent();
				mainImageBitmap = getResizedBitmap( contentWidth, contentHeight, mainImageRef );

				float scaleX = mainImageBitmap.getWidth() / fullSizeRect.width();
				float scaleY = mainImageBitmap.getHeight() / fullSizeRect.height();

				for ( ZoneHolder zoneHolder : zoneHolders ) {
					float newZoneL = zoneHolder.originalSpot.zoneX * scaleX;
					float newZoneT = zoneHolder.originalSpot.zoneY * scaleY;
					float newZoneR = newZoneL + (zoneHolder.originalSpot.zoneW * scaleX);
					float newZoneB = newZoneT + (zoneHolder.originalSpot.zoneH * scaleY);
					zoneHolder.scaledZoneRect.set( newZoneL, newZoneT, newZoneR, newZoneB );

					if ( zoneHolder.imageRef != null ) {
						RectF overlayFullSizeRect = getOriginalImageSize( zoneHolder.imageRef );
						float overlayScaledWidth = overlayFullSizeRect.width() * scaleX;
						float overlayScaledHeight = overlayFullSizeRect.height() * scaleY;
						zoneHolder.imageBitmap = getResizedBitmap( overlayScaledWidth, overlayScaledHeight, zoneHolder.imageRef );

						float outlineL = zoneHolder.originalSpot.x * scaleX;
						float outlineT = zoneHolder.originalSpot.y * scaleY;
						float outlineR = outlineL + overlayScaledWidth;
						float outlineB = outlineT + overlayScaledHeight;
						zoneHolder.imageOutlineRect.set( outlineL, outlineT, outlineR, outlineB );
					}
				}
			}
			catch ( IOException e ) {
				logger.error( "Error resizing content: {}", e );
				Toast.makeText( this.getContext(), "Error resizing content", Toast.LENGTH_LONG ).show();
				reset();
			}
		}
	}

	protected RectF getOriginalImageSize( ByteReference imageRef ) throws IOException {
		InputStream is = null;
		try {
			is = imageRef.getInputStream();
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			BitmapFactory.decodeStream( is, null, opts );
			float originalWidth = opts.outWidth;
			float originalHeight = opts.outHeight;

			return new RectF( 0, 0, originalWidth, originalHeight );
		}
		finally {
			try {if ( is != null ) is.close();} catch ( IOException e ) {}
		}
	}

	/**
	 * Returns an efficiently scaled version of an image.
	 */
	protected Bitmap getResizedBitmap( float targetWidth, float targetHeight, ByteReference imageRef ) throws IOException {
		if ( targetWidth <= 0 || targetHeight <= 0 ) throw new IllegalArgumentException( "Target width and height must be > 0" );

		InputStream boundsStream = null;
		InputStream roughStream = null;
		try {
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			boundsStream = imageRef.getInputStream();
			BitmapFactory.decodeStream( boundsStream, null, opts );
			boundsStream.close();
			float originalWidth = opts.outWidth;
			float originalHeight = opts.outHeight;

			opts = new BitmapFactory.Options();
			opts.inSampleSize = (int)Math.max( originalWidth/targetWidth, originalHeight/targetHeight );
			roughStream = imageRef.getInputStream();
			Bitmap roughBitmap = BitmapFactory.decodeStream( roughStream, null, opts );
			if ( roughBitmap == null ) throw new IOException( "Failed to read bitmap" );
			roughStream.close();

			Matrix m = new Matrix();
			RectF roughRect = new RectF( 0, 0, roughBitmap.getWidth(), roughBitmap.getHeight() );
			RectF targetRect = new RectF( 0, 0, targetWidth, targetHeight );
			m.setRectToRect( roughRect, targetRect, Matrix.ScaleToFit.CENTER );
			float[] values = new float[9];
			m.getValues( values );

			Bitmap resizedBitmap = Bitmap.createScaledBitmap( roughBitmap, (int)(roughBitmap.getWidth() * values[0]), (int)(roughBitmap.getHeight() * values[4]), true );

			// TODO: Maybe convert to device's pixel format (RGBA8888?).

			roughBitmap.recycle();
			return resizedBitmap;
		}
		finally {
			try {if ( boundsStream != null ) boundsStream.close();} catch ( IOException e ) {}
			try {if ( roughStream != null ) roughStream.close();} catch ( IOException e ) {}
		}
	}

	@Override
	protected void onDraw( Canvas canvas ) {
		super.onDraw( canvas );

		if ( mainImageBitmap != null ) {
			canvas.drawBitmap( mainImageBitmap, 0, 0, null );
		}

		int zoneHoldersCount = zoneHolders.size();
		for ( int i=0; i < zoneHoldersCount; i++ ) {
			ZoneHolder zoneHolder = zoneHolders.get( i );

			if ( zoneHolder.imageBitmap != null ) {
				if ( zoneHolder.revealed ) {
					canvas.drawBitmap( zoneHolder.imageBitmap, zoneHolder.imageOutlineRect.left, zoneHolder.imageOutlineRect.top, null );
				}
				else {
					rectPaint.setColor( overlayOutlineColor );
					canvas.drawRect( zoneHolder.imageOutlineRect, rectPaint );

					rectPaint.setColor( overlayZoneColor );
					canvas.drawRect( zoneHolder.scaledZoneRect, rectPaint );
				}
			}
			else if ( zoneHolder.linkTarget != -1 ) {
				rectPaint.setColor( linkColor );
				canvas.drawRect( zoneHolder.scaledZoneRect, rectPaint );
			}
			else {
				rectPaint.setColor( otherColor );
				canvas.drawRect( zoneHolder.scaledZoneRect, rectPaint );
			}
		}

	}



	private static class ZoneHolder {
		public HotSpot originalSpot = null;
		public String title = null;
		public ByteReference imageRef;
		public int linkTarget = -1;

		public Bitmap imageBitmap = null;
		public RectF imageOutlineRect = new RectF();
		public RectF scaledZoneRect = new RectF();

		public boolean revealed = false;
	}
}
