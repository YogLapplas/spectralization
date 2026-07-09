param(
    [int]$Size = 512,
    [string]$OutputDir = "src/main/resources/assets/spectralization/textures/effect"
)

Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies "System.Drawing.dll" -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

public static class SpectralSpotTextureGenerator
{
    public static void WriteAll(int size, string outputDir)
    {
        Directory.CreateDirectory(outputDir);
        WriteTexture(size, Path.Combine(outputDir, "spot_core.png"), 0);
        WriteTexture(size, Path.Combine(outputDir, "spot_halo.png"), 1);
        WriteTexture(size, Path.Combine(outputDir, "spot_ring.png"), 2);
        WriteSquareTexture(size, Path.Combine(outputDir, "spot_square_core.png"), 0);
        WriteSquareTexture(size, Path.Combine(outputDir, "spot_square_halo.png"), 1);
        WriteSquareTexture(size, Path.Combine(outputDir, "spot_square_ring.png"), 2);
    }

    private static void WriteTexture(int size, string path, int kind)
    {
        using (var bitmap = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        {
            var rect = new Rectangle(0, 0, size, size);
            var data = bitmap.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);

            try
            {
                var bytes = new byte[data.Stride * size];

                for (int y = 0; y < size; y++)
                {
                    double v = ((y + 0.5) / size) * 2.0 - 1.0;

                    for (int x = 0; x < size; x++)
                    {
                        double u = ((x + 0.5) / size) * 2.0 - 1.0;
                        double r2 = u * u + v * v;
                        double r = Math.Sqrt(r2);
                        byte alpha = AlphaForKind(kind, r2, r);
                        int offset = y * data.Stride + x * 4;
                        bytes[offset] = 255;
                        bytes[offset + 1] = 255;
                        bytes[offset + 2] = 255;
                        bytes[offset + 3] = alpha;
                    }
                }

                Marshal.Copy(bytes, 0, data.Scan0, bytes.Length);
            }
            finally
            {
                bitmap.UnlockBits(data);
            }

            bitmap.Save(path, ImageFormat.Png);
        }
    }

    private static byte AlphaForKind(int kind, double r2, double r)
    {
        if (r > 1.0)
        {
            return 0;
        }

        double support = EdgeSupport(r);
        double value;

        switch (kind)
        {
            case 0:
                value = 255.0 * Math.Exp(-2.15 * r2) * support;
                break;
            case 1:
                value = 180.0 * Math.Exp(-1.20 * r2) * support;
                break;
            case 2:
                double band = Math.Exp(-Math.Pow((r - 0.72) / 0.18, 2.0));
                double fill = 0.08 + 0.92 * band;
                value = 76.0 * fill * support;
                break;
            default:
                throw new ArgumentOutOfRangeException("kind");
        }

        return QuantizedAlpha(value);
    }

    private static void WriteSquareTexture(int size, string path, int kind)
    {
        using (var bitmap = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        {
            var rect = new Rectangle(0, 0, size, size);
            var data = bitmap.LockBits(rect, ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);

            try
            {
                var bytes = new byte[data.Stride * size];

                for (int y = 0; y < size; y++)
                {
                    double v = ((y + 0.5) / size) * 2.0 - 1.0;

                    for (int x = 0; x < size; x++)
                    {
                        double u = ((x + 0.5) / size) * 2.0 - 1.0;
                        double s = Math.Max(Math.Abs(u), Math.Abs(v));
                        double p4 = u * u * u * u + v * v * v * v;
                        byte alpha = SquareAlphaForKind(kind, p4, s);
                        int offset = y * data.Stride + x * 4;
                        bytes[offset] = 255;
                        bytes[offset + 1] = 255;
                        bytes[offset + 2] = 255;
                        bytes[offset + 3] = alpha;
                    }
                }

                Marshal.Copy(bytes, 0, data.Scan0, bytes.Length);
            }
            finally
            {
                bitmap.UnlockBits(data);
            }

            bitmap.Save(path, ImageFormat.Png);
        }
    }

    private static byte SquareAlphaForKind(int kind, double p4, double s)
    {
        double support = SquareEdgeSupport(s);
        double value;

        switch (kind)
        {
            case 0:
                value = 255.0 * Math.Exp(-1.05 * p4) * support;
                break;
            case 1:
                value = 170.0 * Math.Exp(-0.55 * p4) * support;
                break;
            case 2:
                double band = Math.Exp(-Math.Pow((s - 0.76) / 0.18, 2.0));
                double fill = 0.10 + 0.90 * band;
                value = 72.0 * fill * support;
                break;
            default:
                throw new ArgumentOutOfRangeException("kind");
        }

        return QuantizedAlpha(value);
    }

    private static double SquareEdgeSupport(double s)
    {
        double cutoff = 1.0 - SmoothStep(0.955, 1.0, s);
        return 0.035 + 0.965 * cutoff;
    }

    private static double EdgeSupport(double r)
    {
        if (r > 1.0)
        {
            return 0.0;
        }

        double cutoff = 1.0 - SmoothStep(0.965, 1.0, r);
        return 0.025 + 0.975 * cutoff;
    }

    private static double SmoothStep(double edge0, double edge1, double value)
    {
        double t = Clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double Clamp01(double value)
    {
        if (double.IsNaN(value) || double.IsInfinity(value))
        {
            return 0.0;
        }

        return Math.Max(0.0, Math.Min(1.0, value));
    }

    private static byte QuantizedAlpha(double value)
    {
        if (value <= 0.0)
        {
            return 0;
        }

        return (byte)Math.Max(1, Math.Min(255, (int)Math.Round(value)));
    }
}
"@

[SpectralSpotTextureGenerator]::WriteAll($Size, (Resolve-Path $OutputDir).Path)
