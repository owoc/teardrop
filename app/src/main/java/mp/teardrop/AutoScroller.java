package mp.teardrop;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

/**
 * A horizontal scroll view that automatically scrolls to the extreme right whenever its layout changes.
 */
public class AutoScroller extends HorizontalScrollView {

	public AutoScroller(Context context) {
		super(context);
	}

	public AutoScroller(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public AutoScroller(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		smoothScrollTo(9999, 0); //fullScroll seems not to work properly when the view has a padding set
	}

}
