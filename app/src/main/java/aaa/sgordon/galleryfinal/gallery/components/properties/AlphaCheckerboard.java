package aaa.sgordon.galleryfinal.gallery.components.properties;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;

//Thanks ChatGPT!
public class AlphaCheckerboard {
	public static Drawable createCheckerboardDrawable(int size, int color1, int color2) {
		// Create a tiny bitmap
		Bitmap bitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		Paint paint = new Paint();
		paint.setStyle(Paint.Style.FILL);

		// Draw the 4 squares
		paint.setColor(color1);
		canvas.drawRect(0, 0, size, size, paint);
		canvas.drawRect(size, size, size * 2, size * 2, paint);

		paint.setColor(color2);
		canvas.drawRect(size, 0, size * 2, size, paint);
		canvas.drawRect(0, size, size, size * 2, paint);

		// Create a shader that repeats
		BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

		Paint shaderPaint = new Paint();
		shaderPaint.setShader(shader);

		// Wrap the shader in a Drawable
		ShapeDrawable drawable = new ShapeDrawable(new RectShape());
		drawable.getPaint().setShader(shader);

		return drawable;
	}

}
