package br.com.adagio.adagioagendadigital.ui.activities.main.fragments.home.utils.day_decorator;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.time.LocalDateTime;
import java.util.ArrayList;

@RequiresApi(api = Build.VERSION_CODES.O)
public class AdagioDayLowDecorator extends AdagioDayDecorator{
    public AdagioDayLowDecorator(ArrayList<LocalDateTime> dates, Drawable drawable,int color) {
        super(dates,drawable,color);
    }
}
